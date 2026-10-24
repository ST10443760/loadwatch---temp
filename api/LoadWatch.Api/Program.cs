using LoadWatch.Api.Auth;
using LoadWatch.Api.Data;
using LoadWatch.Api.Health;
using LoadWatch.Api.Json;
using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Mvc.ModelBinding.Metadata;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.

// Read from ConnectionStrings:LoadWatchDb. appsettings.Development.json points at LocalDB; on Azure the portal's
// connection string named LoadWatchDb arrives as an environment variable and overrides it. No secret is committed.
var connectionString = builder.Configuration.GetConnectionString("LoadWatchDb");
if (string.IsNullOrWhiteSpace(connectionString))
{
    throw new InvalidOperationException("The connection string 'LoadWatchDb' must be configured.");
}
builder.Services.AddDbContext<LoadWatchDbContext>(options =>
    options.UseSqlServer(SqlServerResilience.WithResumeTimeout(connectionString), SqlServerResilience.Configure));
builder.Services.AddSingleton(TimeProvider.System);
// Migrations and seeding run in the background with retries, so a paused Azure SQL database can resume
builder.Services.AddSingleton<DatabaseReadiness>();
builder.Services.AddHostedService<DatabaseStartupService>();
builder.Services.AddScoped<UserProfileService>();
builder.Services.AddScoped<AccuracyService>();
builder.Services.Configure<OutageReportOptions>(builder.Configuration.GetSection(OutageReportOptions.SectionName));
builder.Services.AddFirebaseAuthentication(builder.Configuration);
builder.Services.AddLoadWatchHealthChecks();

builder.Services.AddControllers(options =>
    {
        options.ModelBinderProviders.Insert(0, new UtcDateTimeOffsetModelBinderProvider());
        // Report validation errors under the camelCase JSON field names the client sends
        options.ModelMetadataDetailsProviders.Add(new SystemTextJsonValidationMetadataProvider());
        options.Filters.Add<RouteUserIdFilter>();
        options.Filters.Add<UserProfileFilter>();
    })
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new StrictEnumConverterFactory());
        options.JsonSerializerOptions.Converters.Add(new UtcDateTimeOffsetConverter());
    })
    .ConfigureApiBehaviorOptions(options =>
        options.InvalidModelStateResponseFactory = ValidationResponses.Create);

// Every error, including unhandled exceptions and unmatched routes, is returned as ProblemDetails JSON
builder.Services.AddProblemDetails();
// Learn more about configuring Swagger/OpenAPI at https://aka.ms/aspnetcore/swashbuckle
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// Configure the HTTP request pipeline.
app.UseExceptionHandler();
app.UseStatusCodePages();
app.UseDatabaseReadinessGate();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();
app.MapLoadWatchHealth();
app.MapLoadWatchRoot();

app.Run();

// Lets the integration tests host the API with WebApplicationFactory<Program>
public partial class Program
{
}
