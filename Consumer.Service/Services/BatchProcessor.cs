namespace Consumer.Service.Services;

using Consumer.Service.Interfaces;
using Consumer.Service.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

public class BatchProcessor : IBatchProcessor
{
    private readonly ILogger<BatchProcessor> _logger;
    private readonly IFileWriter _fileWriter;
    private readonly IFileCounterManager _fileCounterManager;
    private readonly int _batchSize;
    private readonly List<Product> _currentBatch;
    private readonly SemaphoreSlim _semaphore;

    public BatchProcessor(
        ILogger<BatchProcessor> logger,
        IFileWriter fileWriter,
        IFileCounterManager fileCounterManager,
        IOptions<ConsumerSettings> settings)
    {
        _logger = logger;
        _fileWriter = fileWriter;
        _fileCounterManager = fileCounterManager;
        _batchSize = settings.Value.BatchSize;
        _currentBatch = new List<Product>(_batchSize);
        _semaphore = new SemaphoreSlim(1, 1);
    }

    public async Task AddProductAsync(Product product)
    {
        await _semaphore.WaitAsync();
        try
        {
            _currentBatch.Add(product);
            
            _logger.LogDebug(
                "Added product {ProductId} from order {OrderId} to batch. Current batch size: {BatchSize}",
                product.ProductId,
                product.OrderId,
                _currentBatch.Count);

            // Check if batch is full
            if (_currentBatch.Count >= _batchSize)
            {
                await WriteBatchAsync();
            }
        }
        finally
        {
            _semaphore.Release();
        }
    }

    public async Task FlushAsync()
    {
        await _semaphore.WaitAsync();
        try
        {
            if (_currentBatch.Count > 0)
            {
                _logger.LogInformation(
                    "Flushing partial batch with {ProductCount} products",
                    _currentBatch.Count);
                
                await WriteBatchAsync();
            }
            else
            {
                _logger.LogDebug("No products in batch to flush");
            }
        }
        finally
        {
            _semaphore.Release();
        }
    }

    private async Task WriteBatchAsync()
    {
        var fileCounter = _fileCounterManager.GetNextCounter();
        var productsToWrite = _currentBatch.ToList();

        _logger.LogInformation(
            "Writing batch of {ProductCount} products to file counter {FileCounter}",
            productsToWrite.Count,
            fileCounter);

        await _fileWriter.WriteProductsAsync(productsToWrite, fileCounter);
        
        _fileCounterManager.IncrementCounter();
        _currentBatch.Clear();

        _logger.LogDebug("Batch cleared after successful write");
    }
}
