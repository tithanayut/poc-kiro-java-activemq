namespace Consumer.Service;

using Consumer.Service.Interfaces;

public class Worker : BackgroundService
{
    private readonly ILogger<Worker> _logger;
    private readonly IMessageConsumer _messageConsumer;

    public Worker(ILogger<Worker> logger, IMessageConsumer messageConsumer)
    {
        _logger = logger;
        _messageConsumer = messageConsumer;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            _logger.LogInformation("Consumer service starting at: {time}", DateTimeOffset.Now);
            
            // Start the message consumer
            await _messageConsumer.StartAsync(stoppingToken);
            
            // Keep the worker running until cancellation is requested
            await Task.Delay(Timeout.Infinite, stoppingToken);
        }
        catch (OperationCanceledException)
        {
            _logger.LogInformation("Consumer service is stopping due to cancellation");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Fatal error in Consumer service");
            throw;
        }
    }

    public override async Task StopAsync(CancellationToken cancellationToken)
    {
        _logger.LogInformation("Consumer service stopping at: {time}", DateTimeOffset.Now);
        
        // Stop the message consumer gracefully
        await _messageConsumer.StopAsync(cancellationToken);
        
        await base.StopAsync(cancellationToken);
    }
}
