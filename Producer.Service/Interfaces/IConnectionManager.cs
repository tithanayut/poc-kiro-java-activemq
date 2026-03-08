using Apache.NMS;

namespace Producer.Service.Interfaces;

/// <summary>
/// Manages ActiveMQ connection lifecycle with automatic reconnection and exponential backoff.
/// </summary>
public interface IConnectionManager
{
    /// <summary>
    /// Gets the current ActiveMQ connection, establishing it if necessary.
    /// </summary>
    /// <returns>The active IConnection instance.</returns>
    Task<IConnection> GetConnectionAsync();

    /// <summary>
    /// Attempts to reconnect to ActiveMQ using exponential backoff strategy.
    /// </summary>
    Task ReconnectAsync();

    /// <summary>
    /// Gets a value indicating whether the connection is currently established.
    /// </summary>
    bool IsConnected { get; }
}
