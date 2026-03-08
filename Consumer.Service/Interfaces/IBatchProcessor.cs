namespace Consumer.Service.Interfaces;

using Consumer.Service.Models;

public interface IBatchProcessor
{
    Task AddProductAsync(Product product);
    Task FlushAsync();
}
