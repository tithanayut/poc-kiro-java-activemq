using Apache.NMS;
using Apache.NMS.ActiveMQ;
using Microsoft.Extensions.Options;
using Producer.Service.Interfaces;
using Producer.Service.Models;

namespace Producer.Service.Services;

/// <summary>
/// Manages ActiveMQ connection lifecycle with automatic reconnection and exponential backoff.
/// </summary>
public class ConnectionManager : IConnectionManager, IDisposable
{
    private readonly ILogger<ConnectionManager> _logger;
    private readonly ProducerSettings _settings;
    private readonly SemaphoreSlim _connectionLock = new(1, 1);
    private IConnection? _connection;
    private bool _isConnected;
    private bool _disposed;

    public ConnectionManager(ILogger<ConnectionManager> logger, IOptions<ProducerSettings> settings)
    {
        _logger = logger;
        _settings = settings.Value;
    }

    /// <inheritdoc />
    public bool IsConnected => _isConnected;

    /// <inheritdoc />
    public async Task<IConnection> GetConnectionAsync()
    {
        await _connectionLock.WaitAsync();
        try
        {
            if (_connection != null && _isConnected)
            {
                return _connection;
            }

            await EstablishConnectionAsync();
            return _connection!;
        }
        finally
        {
            _connectionLock.Release();
        }
    }

    /// <inheritdoc />
    public async Task ReconnectAsync()
    {
        await _connectionLock.WaitAsync();
        try
        {
            _logger.LogWarning("Starting reconnection process to ActiveMQ");
            
            // Close existing connection if any
            CloseConnection();

            int attempt = 0;
            while (!_isConnected)
            {
                var delay = CalculateBackoffDelay(attempt);
                _logger.LogWarning("Reconnection attempt {Attempt}, waiting {DelayMs}ms before retry", 
                    attempt + 1, delay);

                await Task.Delay(delay);

                try
                {
                    await EstablishConnectionAsync();
                    _logger.LogInformation("Successfully reconnected to ActiveMQ after {Attempts} attempts", 
                        attempt + 1);
                    break;
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Reconnection attempt {Attempt} failed", attempt + 1);
                    attempt++;
                }
            }
        }
        finally
        {
            _connectionLock.Release();
        }
    }

    private async Task EstablishConnectionAsync()
    {
        try
        {
            var factory = new ConnectionFactory(_settings.BrokerUrl);
            _connection = await Task.Run(() => factory.CreateConnection());
            
            // Register exception listener to detect connection loss
            _connection.ExceptionListener += OnConnectionException;
            
            await Task.Run(() => _connection.Start());
            _isConnected = true;
            
            _logger.LogInformation("Successfully connected to ActiveMQ at {BrokerUrl}", _settings.BrokerUrl);
        }
        catch (Exception ex)
        {
            _isConnected = false;
            _logger.LogError(ex, "Failed to establish connection to ActiveMQ at {BrokerUrl}", _settings.BrokerUrl);
            throw;
        }
    }

    private void OnConnectionException(Exception exception)
    {
        _logger.LogError(exception, "ActiveMQ connection lost, triggering reconnection");
        _isConnected = false;
        
        // Trigger reconnection in background
        _ = Task.Run(async () =>
        {
            try
            {
                await ReconnectAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Background reconnection failed");
            }
        });
    }

    private int CalculateBackoffDelay(int attempt)
    {
        // Exponential backoff: delay = min(initialDelay * (multiplier ^ attempt), maxDelay)
        var delay = _settings.Retry.InitialDelayMs * Math.Pow(_settings.Retry.BackoffMultiplier, attempt);
        return (int)Math.Min(delay, _settings.Retry.MaxDelayMs);
    }

    private void CloseConnection()
    {
        if (_connection != null)
        {
            try
            {
                _connection.ExceptionListener -= OnConnectionException;
                _connection.Close();
                _connection.Dispose();
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error while closing existing connection");
            }
            finally
            {
                _connection = null;
                _isConnected = false;
            }
        }
    }

    public void Dispose()
    {
        if (_disposed)
            return;

        CloseConnection();
        _connectionLock.Dispose();
        _disposed = true;
        
        GC.SuppressFinalize(this);
    }
}
