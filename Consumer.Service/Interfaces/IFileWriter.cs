namespace Consumer.Service.Interfaces;

using Consumer.Service.Models;

public interface IFileWriter
{
    Task WriteProductsAsync(IEnumerable<Product> products, int fileCounter);
}
