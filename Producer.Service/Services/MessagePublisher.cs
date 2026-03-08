using System.Text.Json;
using Apache.NMS;
using Microsoft.Extensions.Options;
using Producer.Service.Interfaces;
using Producer.Service.Models;

namespace Producer.Service.Services;

/// <summary>
/// Publishes product messages to ActiveMQ topic with JSON serialization.
/// </summary>
public class MessagePublisher : IMessagePublisher
{
    private readonly IConnectionManager _connectionManager;
    private readonly ILogger<MessagePublisher> _logger;
    private readonly ProducerSettings _settings;

    public MessagePublisher(
        IConnectionManager connectionManager,
        ILogger<MessagePublisher> logger,
        IOptions<ProducerSettings> settings)
    {
        _connectionManager = connectionManager;
        _logger = logger;
        _settings = settings.Value;

        connectionManager.ReconnectAsync();
    }

    /// <inheritdoc />
    public bool IsConnected => _connectionManager.IsConnected;

    /// <inheritdoc />
    public async Task PublishProductMessagesAsync(string orderId, IEnumerable<string> productIds)
    {
        var productIdList = productIds.ToList();
        
        _logger.LogDebug("Publishing {Count} product messages for order {OrderId}", 
            productIdList.Count, orderId);

        try
        {
            var connection = await _connectionManager.GetConnectionAsync();
            
            using var session = await Task.Run(() => connection.CreateSession(AcknowledgementMode.AutoAcknowledge));
            using var destination = await Task.Run(() => session.GetTopic(_settings.TopicName));
            using var producer = await Task.Run(() => session.CreateProducer(destination));

            foreach (var productId in productIdList)
            {
                var productMessage = new ProductMessage
                {
                    OrderId = orderId,
                    ProductId = productId
                };

                var json = JsonSerializer.Serialize(productMessage);
                var textMessage = await Task.Run(() => session.CreateTextMessage(json));
                
                await Task.Run(() => producer.Send(textMessage));
                
                _logger.LogDebug("Published message for order {OrderId}, product {ProductId} to topic {TopicName}", 
                    orderId, productId, _settings.TopicName);
            }

            _logger.LogDebug("Successfully published {Count} messages for order {OrderId}", 
                productIdList.Count, orderId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to publish messages for order {OrderId}", orderId);
            throw;
        }
    }
}
