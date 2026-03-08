namespace Consumer.Service.Services;

using Consumer.Service.Interfaces;
using Consumer.Service.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

public class FileWriter : IFileWriter
{
    private readonly ILogger<FileWriter> _logger;
    private readonly FileWriterSettings _settings;
    private readonly string _outputDirectory;

    public FileWriter(
        ILogger<FileWriter> logger,
        IOptions<ConsumerSettings> consumerSettings)
    {
        _logger = logger;
        _settings = consumerSettings.Value.FileWriter;
        _outputDirectory = consumerSettings.Value.OutputDirectory;
    }

    public async Task WriteProductsAsync(IEnumerable<Product> products, int fileCounter)
    {
        var filename = $"PRODUCT_ORDER_{fileCounter}.txt";
        var filePath = Path.Combine(_outputDirectory, filename);
        var productList = products.ToList();

        // Create output directory if it doesn't exist
        Directory.CreateDirectory(_outputDirectory);

        // Format products as pipe-delimited lines
        var lines = productList.Select(p => $"{p.OrderId}|{p.ProductId}");

        // Implement retry logic
        int attempt = 0;
        Exception? lastException = null;

        while (attempt < _settings.MaxRetryAttempts)
        {
            try
            {
                await File.WriteAllLinesAsync(filePath, lines);
                
                // Log successful write
                _logger.LogInformation(
                    "Successfully wrote {ProductCount} products to file {Filename}",
                    productList.Count,
                    filename);

                // Log warning if this was a retry
                if (attempt > 0)
                {
                    _logger.LogWarning(
                        "File write succeeded on retry attempt {Attempt} for file {Filename}",
                        attempt + 1,
                        filename);
                }

                return;
            }
            catch (Exception ex)
            {
                lastException = ex;
                attempt++;

                if (attempt < _settings.MaxRetryAttempts)
                {
                    _logger.LogWarning(
                        ex,
                        "File write attempt {Attempt} failed for file {Filename}. Retrying in {DelayMs}ms...",
                        attempt,
                        filename,
                        _settings.RetryDelayMs);

                    await Task.Delay(_settings.RetryDelayMs);
                }
            }
        }

        // Max retries exhausted
        _logger.LogError(
            lastException,
            "Failed to write file {Filename} after {MaxAttempts} attempts",
            filename,
            _settings.MaxRetryAttempts);

        throw new IOException(
            $"Failed to write file {filename} after {_settings.MaxRetryAttempts} attempts",
            lastException);
    }
}
