using System.Text.RegularExpressions;
using Consumer.Service.Interfaces;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Consumer.Service.Models;

namespace Consumer.Service.Services;

public class FileCounterManager : IFileCounterManager
{
    private readonly ILogger<FileCounterManager> _logger;
    private readonly string _outputDirectory;
    private int _currentCounter;
    private readonly object _lock = new();

    public FileCounterManager(
        ILogger<FileCounterManager> logger,
        IOptions<ConsumerSettings> settings)
    {
        _logger = logger;
        _outputDirectory = settings.Value.OutputDirectory;
        _currentCounter = InitializeCounter();
        _logger.LogInformation("File counter initialized to {Counter}", _currentCounter);
    }

    public int GetNextCounter()
    {
        lock (_lock)
        {
            return _currentCounter;
        }
    }

    public void IncrementCounter()
    {
        lock (_lock)
        {
            _currentCounter++;
        }
    }

    private int InitializeCounter()
    {
        try
        {
            // Create directory if it doesn't exist
            if (!Directory.Exists(_outputDirectory))
            {
                Directory.CreateDirectory(_outputDirectory);
                return 1;
            }

            // Scan for existing PRODUCT_ORDER_*.txt files
            var files = Directory.GetFiles(_outputDirectory, "PRODUCT_ORDER_*.txt");
            
            if (files.Length == 0)
            {
                return 1;
            }

            // Regex to extract counter from filename: PRODUCT_ORDER_{N}.txt
            var regex = new Regex(@"PRODUCT_ORDER_(\d+)\.txt$", RegexOptions.IgnoreCase);
            var maxCounter = 0;

            foreach (var file in files)
            {
                var fileName = Path.GetFileName(file);
                var match = regex.Match(fileName);

                if (match.Success)
                {
                    if (int.TryParse(match.Groups[1].Value, out var counter))
                    {
                        maxCounter = Math.Max(maxCounter, counter);
                    }
                    else
                    {
                        _logger.LogWarning("Unable to parse counter value from filename: {FileName}", fileName);
                    }
                }
                else
                {
                    _logger.LogWarning("Filename does not match expected pattern: {FileName}", fileName);
                }
            }

            return maxCounter + 1;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error initializing file counter from directory: {Directory}", _outputDirectory);
            return 1;
        }
    }
}
