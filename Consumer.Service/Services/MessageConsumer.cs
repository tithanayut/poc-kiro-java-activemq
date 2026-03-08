namespace Consumer.Service.Services;

using Apache.NMS;
using Apache.NMS.ActiveMQ;
using Consumer.Service.Interfaces;
using Consumer.Service.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using System.Text.Json;

public class MessageConsumer : Interfaces.IMessageConsumer
{
    private readonly ILogger<MessageConsumer> _logger;
    private readonly IBatchProcessor _batchProcessor;
    private readonly ConsumerSettings _settings;
    private IConnection? _connection;
    private ISession? _session;
    private Apache.NMS.IMessageConsumer? _consumer;

    public MessageConsumer(
        ILogger<MessageConsumer> logger,
        IBatchProcessor batchProcessor,
        IOptions<ConsumerSettings> settings)
    {
        _logger = logger;
        _batchProcessor = batchProcessor;
        _settings = settings.Value;
    }

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        try
        {
            _logger.LogInformation(
                "Starting MessageConsumer with BrokerUrl: {BrokerUrl}, Topic: {TopicName}, ClientId: {ClientId}, Subscription: {SubscriptionName}",
                _settings.BrokerUrl,
                _settings.TopicName,
                _settings.ClientId,
                _settings.SubscriptionName);

            // Create connection factory
            var factory = new ConnectionFactory(_settings.BrokerUrl);
            
            // Create connection with client ID for durable subscription
            _connection = await factory.CreateConnectionAsync();
            _connection.ClientId = _settings.ClientId;
            
            // Start the connection
            await _connection.StartAsync();

            // Create session with manual acknowledgment mode
            _session = await _connection.CreateSessionAsync(AcknowledgementMode.ClientAcknowledge);

            // Create topic
            var topic = await _session.GetTopicAsync(_settings.TopicName);

            // Create durable subscriber
            _consumer = await _session.CreateDurableConsumerAsync(
                topic,
                _settings.SubscriptionName,
                null,
                false);

            // Set up message listener
            _consumer.Listener += async (message) =>
            {
                await OnMessageAsync(message, cancellationToken);
            };

            _logger.LogInformation("MessageConsumer started successfully");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to start MessageConsumer");
            throw;
        }
    }

    public async Task StopAsync(CancellationToken cancellationToken)
    {
        try
        {
            _logger.LogInformation("Initiating graceful shutdown of MessageConsumer");

            // Flush any remaining products in the batch
            _logger.LogInformation("Flushing partial batch before shutdown");
            await _batchProcessor.FlushAsync();

            // Close consumer
            if (_consumer != null)
            {
                await _consumer.CloseAsync();
                _consumer.Dispose();
                _consumer = null;
            }

            // Close session
            if (_session != null)
            {
                await _session.CloseAsync();
                _session.Dispose();
                _session = null;
            }

            // Close connection
            if (_connection != null)
            {
                _logger.LogInformation("Closing ActiveMQ connection");
                await _connection.CloseAsync();
                _connection.Dispose();
                _connection = null;
            }

            _logger.LogInformation("Graceful shutdown completed successfully");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error during graceful shutdown");
            throw;
        }
    }

    private async Task OnMessageAsync(IMessage message, CancellationToken cancellationToken)
    {
        try
        {
            if (message is ITextMessage textMessage)
            {
                var messageText = textMessage.Text;
                
                // Deserialize JSON message to Product object
                Product? product;
                try
                {
                    product = JsonSerializer.Deserialize<Product>(messageText);
                    
                    if (product == null)
                    {
                        _logger.LogError("Deserialized product is null. Message: {Message}", messageText);
                        // Reject message by not acknowledging
                        return;
                    }

                    if (string.IsNullOrEmpty(product.OrderId) || string.IsNullOrEmpty(product.ProductId))
                    {
                        _logger.LogError(
                            "Product has missing required fields. OrderId: {OrderId}, ProductId: {ProductId}",
                            product.OrderId,
                            product.ProductId);
                        // Reject message by not acknowledging
                        return;
                    }
                }
                catch (JsonException ex)
                {
                    _logger.LogError(ex, "Failed to deserialize message: {Message}", messageText);
                    // Reject message by not acknowledging
                    return;
                }

                _logger.LogDebug(
                    "Received message - OrderId: {OrderId}, ProductId: {ProductId}",
                    product.OrderId,
                    product.ProductId);

                // Add product to batch
                try
                {
                    await _batchProcessor.AddProductAsync(product);
                    
                    // Acknowledge message only after successful batch addition
                    await message.AcknowledgeAsync();
                    
                    _logger.LogDebug(
                        "Acknowledged message - OrderId: {OrderId}, ProductId: {ProductId}",
                        product.OrderId,
                        product.ProductId);
                }
                catch (Exception ex)
                {
                    _logger.LogError(
                        ex,
                        "Failed to add product to batch - OrderId: {OrderId}, ProductId: {ProductId}",
                        product.OrderId,
                        product.ProductId);
                    // Do not acknowledge message - it will be redelivered
                }
            }
            else
            {
                _logger.LogWarning("Received non-text message of type: {MessageType}", message.GetType().Name);
                // Reject message by not acknowledging
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error processing message");
            // Do not acknowledge message - it will be redelivered
        }
    }
}
