namespace Consumer.Service.Models;

public class ConsumerSettings
{
    public string BrokerUrl { get; set; } = string.Empty;
    public string TopicName { get; set; } = string.Empty;
    public string ClientId { get; set; } = string.Empty;
    public string SubscriptionName { get; set; } = string.Empty;
    public int BatchSize { get; set; } = 5;
    public string OutputDirectory { get; set; } = string.Empty;
    public FileWriterSettings FileWriter { get; set; } = new();
}
