namespace LoadWatch.Api.Dtos;

// Search result row. Municipality and supply type are included so similarly named suburbs can be told apart.
public record AreaSummary(
    string AreaId,
    string Name,
    string Municipality,
    string Province,
    string SupplyType);
