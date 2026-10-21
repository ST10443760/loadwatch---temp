using System.Text.Json.Serialization;
using LoadWatch.Api.Data;
using LoadWatch.Api.Json;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.

builder.Services.AddDbContext<LoadWatchDbContext>(options =>
    options.UseSqlServer(builder.Configuration.GetConnectionString("LoadWatchDb")));
builder.Services.AddSingleton(TimeProvider.System);

builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
        options.JsonSerializerOptions.Converters.Add(new UtcDateTimeOffsetConverter());
    });

// Every error, including unhandled exceptions and unmatched routes, is returned as ProblemDetails JSON
builder.Services.AddProblemDetails();
// Learn more about configuring Swagger/OpenAPI at https://aka.ms/aspnetcore/swashbuckle
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<LoadWatchDbContext>();
    await DbSeeder.SeedAsync(db, scope.ServiceProvider.GetRequiredService<TimeProvider>());
}

// Configure the HTTP request pipeline.
app.UseExceptionHandler();
app.UseStatusCodePages();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseAuthorization();

app.MapControllers();

app.Run();
