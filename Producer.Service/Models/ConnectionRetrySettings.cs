namespace Producer.Service.Models;

public class ConnectionRetrySettings
{
    public int InitialDelayMs { get; set; } = 1000;
    public int MaxDelayMs { get; set; } = 60000;
    public double BackoffMultiplier { get; set; } = 2.0;
}
