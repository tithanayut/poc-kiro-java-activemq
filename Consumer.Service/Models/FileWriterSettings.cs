namespace Consumer.Service.Models;

public class FileWriterSettings
{
    public int MaxRetryAttempts { get; set; } = 3;
    public int RetryDelayMs { get; set; } = 1000;
}
