using Consumer.Service;
using Consumer.Service.Interfaces;
using Consumer.Service.Models;
using Consumer.Service.Services;
using Microsoft.Extensions.Options;
using System.Text.Json;

IHost host = Host.CreateDefaultBuilder(args)
    .ConfigureServices((context, services) =>
    {
        // Bind configuration
        services.Configure<ConsumerSettings>(context.Configuration.GetSection("Consumer"));

        // Configure JSON serialization options
        services.Configure<JsonSerializerOptions>(options =>
        {
            options.PropertyNameCaseInsensitive = true;
            options.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
        });

        // Register services
        services.AddSingleton<IFileCounterManager, FileCounterManager>();
        services.AddSingleton<IFileWriter, FileWriter>();
        services.AddSingleton<IBatchProcessor, BatchProcessor>();
        services.AddSingleton<IMessageConsumer, MessageConsumer>();

        // Register worker
        services.AddHostedService<Worker>();
    })
    .Build();

// Log successful startup
var logger = host.Services.GetRequiredService<ILogger<Program>>();
var settings = host.Services.GetRequiredService<IOptions<ConsumerSettings>>().Value;
logger.LogInformation(
    "Consumer service started successfully - BrokerUrl: {BrokerUrl}, Topic: {TopicName}, ClientId: {ClientId}, Subscription: {SubscriptionName}",
    settings.BrokerUrl,
    settings.TopicName,
    settings.ClientId,
    settings.SubscriptionName);

host.Run();
