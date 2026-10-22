using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

namespace LoadWatch.Api.Auth;

// Validates Firebase ID tokens sent as "Authorization: Bearer <token>". Signatures are checked against
// Google's public keys, discovered from the issuer's OpenID metadata, so no service account file is needed.
public static class FirebaseAuthentication
{
    public const string ProjectIdKey = "Firebase:ProjectId";

    public static IServiceCollection AddFirebaseAuthentication(this IServiceCollection services, IConfiguration configuration)
    {
        // Overridable on Azure with the environment variable Firebase__ProjectId
        var projectId = configuration[ProjectIdKey];
        if (string.IsNullOrWhiteSpace(projectId))
        {
            throw new InvalidOperationException($"{ProjectIdKey} must be configured.");
        }

        var issuer = $"https://securetoken.google.com/{projectId}";

        services
            .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(options =>
            {
                options.Authority = issuer;
                // Keep the token's own claim names (sub, name, email) instead of the legacy SOAP claim URIs
                options.MapInboundClaims = false;
                options.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuer = true,
                    ValidIssuer = issuer,
                    ValidateAudience = true,
                    ValidAudience = projectId,
                    ValidateLifetime = true,
                    ValidateIssuerSigningKey = true,
                    NameClaimType = FirebaseClaims.UserId,
                };
                options.Events = new JwtBearerEvents { OnChallenge = WriteUnauthorizedProblemAsync };
            });

        services.AddAuthorization();
        return services;
    }

    // Replaces the default empty 401 with a ProblemDetails body that tells the client what to do
    private static async Task WriteUnauthorizedProblemAsync(JwtBearerChallengeContext context)
    {
        context.HandleResponse();

        var (header, detail) = context.AuthenticateFailure switch
        {
            null => ("Bearer",
                "A Firebase ID token is required. Send it as 'Authorization: Bearer <token>'."),
            SecurityTokenExpiredException => ("Bearer error=\"invalid_token\", error_description=\"The token expired\"",
                "The Firebase ID token has expired. Refresh it and try again."),
            _ => ("Bearer error=\"invalid_token\"",
                "The Firebase ID token is invalid."),
        };

        var response = context.Response;
        response.StatusCode = StatusCodes.Status401Unauthorized;
        response.Headers.WWWAuthenticate = header;

        var problemDetails = context.HttpContext.RequestServices.GetRequiredService<IProblemDetailsService>();
        await problemDetails.WriteAsync(new ProblemDetailsContext
        {
            HttpContext = context.HttpContext,
            ProblemDetails =
            {
                Status = StatusCodes.Status401Unauthorized,
                Title = "Not signed in",
                Detail = detail,
            },
        });
    }
}
