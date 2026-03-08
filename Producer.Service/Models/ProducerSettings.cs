namespace Producer.Service.Models;

public class ProducerSettings
{
    public string BrokerUrl { get; set; } = string.Empty;
    public string TopicName { get; set; } = string.Empty;
    public ConnectionRetrySettings Retry { get; set; } = new();
}
