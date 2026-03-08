namespace Producer.Service.Interfaces;

/// <summary>
/// Publishes product messages to ActiveMQ topic.
/// </summary>
public interface IMessagePublisher
{
    /// <summary>
    /// Publishes individual product messages for each product ID in an order.
    /// </summary>
    /// <param name="orderId">The order identifier.</param>
    /// <param name="productIds">The collection of product identifiers to publish.</param>
    /// <returns>A task representing the asynchronous operation.</returns>
    Task PublishProductMessagesAsync(string orderId, IEnumerable<string> productIds);

    /// <summary>
    /// Gets a value indicating whether the message publisher is connected to ActiveMQ.
    /// </summary>
    bool IsConnected { get; }
}
