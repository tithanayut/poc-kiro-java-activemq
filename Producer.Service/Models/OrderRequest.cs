using System.ComponentModel.DataAnnotations;

namespace Producer.Service.Models;

public class OrderRequest
{
    [Required]
    public string OrderId { get; set; } = string.Empty;
    
    [Required]
    [MinLength(1)]
    public List<string> ProductIds { get; set; } = new();
}
