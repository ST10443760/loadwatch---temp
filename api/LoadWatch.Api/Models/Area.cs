namespace LoadWatch.Api.Models;

// Public catalogue entity. Owned by the server and never written by the client.
public class Area
{
    public string AreaId { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Municipality { get; set; } = string.Empty;
    public string Province { get; set; } = string.Empty;

    // ESKOM or MUNICIPAL
    public string SupplyType { get; set; } = string.Empty;

    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public bool IsActive { get; set; }
}
