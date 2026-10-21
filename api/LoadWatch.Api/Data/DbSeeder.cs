using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Data;

// Seeds the Durban area catalogue, the current stage and a rolling schedule so the
// client always has a next event to count down to.
public static class DbSeeder
{
    private const string Municipality = "eThekwini Metropolitan Municipality";
    private const string Province = "KwaZulu-Natal";
    private const int ScheduleDays = 14;
    private const int SlotsPerDay = 6;
    private static readonly TimeSpan SlotSpacing = TimeSpan.FromHours(4);
    private static readonly TimeSpan SlotLength = TimeSpan.FromMinutes(150);

    // South Africa Standard Time has no daylight saving, so a fixed offset is enough
    private static readonly TimeSpan Sast = TimeSpan.FromHours(2);

    private static readonly Area[] Areas =
    [
        NewArea("eth-durban-north", "Durban North", "MUNICIPAL", -29.7870, 31.0370),
        NewArea("eth-umhlanga", "Umhlanga", "MUNICIPAL", -29.7260, 31.0850),
        NewArea("eth-la-lucia", "La Lucia", "MUNICIPAL", -29.7570, 31.0590),
        NewArea("eth-westville", "Westville", "MUNICIPAL", -29.8320, 30.9250),
        NewArea("eth-berea", "Berea", "MUNICIPAL", -29.8480, 31.0000),
        NewArea("eth-morningside", "Morningside", "MUNICIPAL", -29.8260, 31.0160),
        NewArea("eth-musgrave", "Musgrave", "MUNICIPAL", -29.8460, 30.9960),
        NewArea("eth-glenwood", "Glenwood", "MUNICIPAL", -29.8690, 30.9840),
        NewArea("eth-durban-central", "Durban Central", "MUNICIPAL", -29.8580, 31.0290),
        NewArea("eth-pinetown", "Pinetown", "MUNICIPAL", -29.8150, 30.8570),
        NewArea("eth-hillcrest", "Hillcrest", "ESKOM", -29.7810, 30.7640),
        NewArea("eth-kloof", "Kloof", "ESKOM", -29.7900, 30.8330),
    ];

    public static async Task SeedAsync(LoadWatchDbContext db, TimeProvider time, CancellationToken ct = default)
    {
        var now = time.GetUtcNow();

        if (!await db.Areas.AnyAsync(ct))
        {
            db.Areas.AddRange(Areas);
        }

        if (!await db.StageStatus.AnyAsync(ct))
        {
            db.StageStatus.Add(new StageStatus { Stage = 2, UpdatedAt = now });
        }

        await db.SaveChangesAsync(ct);

        // Top up each area's schedule so it always runs ScheduleDays ahead of today
        var today = DateOnly.FromDateTime(now.ToOffset(Sast).DateTime);
        var lastDay = today.AddDays(ScheduleDays);
        var latestStarts = await db.ScheduleEvents
            .GroupBy(e => e.AreaId)
            .Select(g => new { AreaId = g.Key, LatestStart = g.Max(e => e.StartAt) })
            .ToDictionaryAsync(x => x.AreaId, x => x.LatestStart, ct);

        var areaIds = await db.Areas.OrderBy(a => a.AreaId).Select(a => a.AreaId).ToListAsync(ct);
        for (var index = 0; index < areaIds.Count; index++)
        {
            var areaId = areaIds[index];
            var fromDay = latestStarts.TryGetValue(areaId, out var latest)
                ? DateOnly.FromDateTime(latest.ToOffset(Sast).DateTime).AddDays(1)
                : today;
            if (fromDay < today)
            {
                fromDay = today;
            }

            for (var day = fromDay; day < lastDay; day = day.AddDays(1))
            {
                db.ScheduleEvents.AddRange(BuildDay(areaId, index, day, now));
            }
        }

        await db.SaveChangesAsync(ct);
    }

    // Each area sits in one of four hourly block groups. The day's stage decides how many
    // 2.5-hour slots it gets, and slots are 4 hours apart so they never overlap.
    private static IEnumerable<ScheduleEvent> BuildDay(string areaId, int areaIndex, DateOnly day, DateTimeOffset now)
    {
        var stage = (day.DayNumber * 3 % 4) + 1;
        var step = stage switch { 1 or 2 => 3, 3 => 2, _ => 1 };
        var firstSlot = (day.DayNumber + areaIndex) % SlotsPerDay;
        var dayStart = new DateTimeOffset(day.ToDateTime(TimeOnly.MinValue), Sast)
            .AddHours(areaIndex % 4);

        for (var i = 0; i < stage; i++)
        {
            var slot = (firstSlot + (i * step)) % SlotsPerDay;
            var startAt = dayStart + (SlotSpacing * slot);
            yield return new ScheduleEvent
            {
                ScheduleId = $"{areaId}-{startAt.UtcDateTime:yyyyMMddHHmm}",
                AreaId = areaId,
                Stage = stage,
                StartAt = startAt.ToUniversalTime(),
                EndAt = (startAt + SlotLength).ToUniversalTime(),
                Source = "SEED",
                UpdatedAt = now,
            };
        }
    }

    private static Area NewArea(string areaId, string name, string supplyType, double latitude, double longitude) => new()
    {
        AreaId = areaId,
        Name = name,
        Municipality = Municipality,
        Province = Province,
        SupplyType = supplyType,
        Latitude = latitude,
        Longitude = longitude,
        IsActive = true,
    };
}
