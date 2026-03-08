using Producer.Service.Interfaces;
using Producer.Service.Models;
using Producer.Service.Services;

var builder = WebApplication.CreateBuilder(args);

// Configure logging
builder.Logging.ClearProviders();
builder.Logging.AddConsole();
builder.Logging.AddDebug();

// Bind ProducerSettings from configuration
builder.Services.Configure<ProducerSettings>(
    builder.Configuration.GetSection("Producer"));

// Register services as singletons
builder.Services.AddSingleton<IConnectionManager, ConnectionManager>();
builder.Services.AddSingleton<IMessagePublisher, MessagePublisher>();

// Configure JSON serialization options
builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.CamelCase;
    options.SerializerOptions.WriteIndented = false;
});

// Add controllers
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.CamelCase;
        options.JsonSerializerOptions.WriteIndented = false;
    });

// Configure Swagger/OpenAPI
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// Configure HTTP server port from configuration (if specified)
var url = builder.Configuration.GetValue<string>("Kestrel:Endpoints:Http:Url");
if (!string.IsNullOrEmpty(url))
{
    builder.WebHost.UseUrls(url);
}

var app = builder.Build();

// Log successful startup
var logger = app.Services.GetRequiredService<ILogger<Program>>();
var settings = app.Services.GetRequiredService<Microsoft.Extensions.Options.IOptions<ProducerSettings>>().Value;
logger.LogInformation("Producer service started successfully");
logger.LogInformation("ActiveMQ Broker URL: {BrokerUrl}", settings.BrokerUrl);
logger.LogInformation("ActiveMQ Topic Name: {TopicName}", settings.TopicName);

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseAuthorization();

app.MapControllers();

app.Run();
