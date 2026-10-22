# Builds the LoadWatch API into a container image that listens on port 8080.
# Build from the api folder: docker build -t loadwatch-api api

# Publish with the full .NET 8 SDK
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

# Restore on its own layer so it is only redone when the project file changes
COPY LoadWatch.Api/LoadWatch.Api.csproj LoadWatch.Api/
RUN dotnet restore LoadWatch.Api/LoadWatch.Api.csproj

COPY LoadWatch.Api/ LoadWatch.Api/
RUN dotnet publish LoadWatch.Api/LoadWatch.Api.csproj --configuration Release --no-restore --output /app/publish /p:UseAppHost=false

# Run on the smaller ASP.NET Core runtime image
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS runtime
WORKDIR /app

ENV ASPNETCORE_HTTP_PORTS=8080
EXPOSE 8080

COPY --from=build /app/publish .

# The runtime image ships a non-root user for this
USER $APP_UID

ENTRYPOINT ["dotnet", "LoadWatch.Api.dll"]
