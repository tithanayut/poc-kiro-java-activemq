using Microsoft.AspNetCore.Mvc;
using Producer.Service.Interfaces;
using Producer.Service.Models;

namespace Producer.Service.Controllers;

/// <summary>
/// Handles HTTP order requests and publishes product messages to ActiveMQ.
/// </summary>
[ApiController]
[Route("orders")]
public class OrderController : ControllerBase
{
    private readonly IMessagePublisher _messagePublisher;
    private readonly ILogger<OrderController> _logger;

    public OrderController(
        IMessagePublisher messagePublisher,
        ILogger<OrderController> logger)
    {
        _messagePublisher = messagePublisher;
        _logger = logger;
    }

    /// <summary>
    /// Creates a new order by publishing product messages to ActiveMQ.
    /// </summary>
    /// <param name="request">The order request containing orderId and productIds.</param>
    /// <returns>202 Accepted if successful, 400 Bad Request if validation fails, 503 Service Unavailable if ActiveMQ is unavailable.</returns>
    [HttpPost]
    public async Task<IActionResult> CreateOrder([FromBody] OrderRequest request)
    {
        _logger.LogInformation("Received order request for orderId: {OrderId} with {ProductCount} products", 
            request.OrderId, request.ProductIds.Count);

        // Check if ActiveMQ connection is available
        if (!_messagePublisher.IsConnected)
        {
            _logger.LogWarning("ActiveMQ connection unavailable for order {OrderId}", request.OrderId);
            return StatusCode(503, "Service Unavailable: ActiveMQ connection is unavailable");
        }

        try
        {
            await _messagePublisher.PublishProductMessagesAsync(request.OrderId, request.ProductIds);
            
            _logger.LogInformation("Successfully published {ProductCount} messages for order {OrderId}", 
                request.ProductIds.Count, request.OrderId);
            
            return Accepted();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to publish messages for order {OrderId}", request.OrderId);
            return StatusCode(500, "Internal Server Error: Failed to publish messages");
        }
    }
}
