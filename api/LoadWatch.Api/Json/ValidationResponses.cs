using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Infrastructure;
using Microsoft.AspNetCore.Mvc.ModelBinding;

namespace LoadWatch.Api.Json;

// Builds the 400 ProblemDetails for invalid requests. Errors from reading the JSON body arrive keyed by
// JSON path ("$.eventType") with System.Text.Json's message, alongside a misleading "The request field is
// required." for the body parameter. This rewrites them to the plain field name and a readable message:
//   { "eventType": ["eventType must be POWER_OFF or POWER_BACK_ON"] }
public static class ValidationResponses
{
    // Converters start their message with this so it can be completed with the field name,
    // e.g. "must be POWER_OFF or POWER_BACK_ON" becomes "eventType must be POWER_OFF or POWER_BACK_ON"
    public const string FieldMessagePrefix = "must be ";

    public static IActionResult Create(ActionContext context)
    {
        var bodyParameters = context.ActionDescriptor.Parameters
            .Where(p => p.BindingInfo?.BindingSource == BindingSource.Body)
            .Select(p => p.Name)
            .ToHashSet();
        var hasJsonErrors = context.ModelState.Any(e => e.Key.StartsWith('$') && e.Value is { Errors.Count: > 0 });

        var errors = new ModelStateDictionary();
        foreach (var (key, entry) in context.ModelState)
        {
            // The body parameter is only "missing" because its JSON could not be read, which is reported below
            if (entry is not { Errors.Count: > 0 } || (hasJsonErrors && bodyParameters.Contains(key)))
            {
                continue;
            }

            foreach (var error in entry.Errors)
            {
                var message = string.IsNullOrEmpty(error.ErrorMessage) ? error.Exception?.Message ?? "" : error.ErrorMessage;
                if (key.StartsWith('$'))
                {
                    var field = key.TrimStart('$').TrimStart('.');
                    errors.AddModelError(field.Length == 0 ? "body" : field, DescribeJsonError(field, message));
                }
                else
                {
                    errors.AddModelError(key, message);
                }
            }
        }

        var problemDetailsFactory = context.HttpContext.RequestServices.GetRequiredService<ProblemDetailsFactory>();
        var problem = problemDetailsFactory.CreateValidationProblemDetails(context.HttpContext, errors);
        return new BadRequestObjectResult(problem) { ContentTypes = { "application/problem+json" } };
    }

    private static string DescribeJsonError(string field, string message)
    {
        // "reports[0].eventType" is described as "eventType"
        var leaf = field[(field.LastIndexOf('.') + 1)..];
        var bracket = leaf.IndexOf('[');
        if (bracket >= 0)
        {
            leaf = leaf[..bracket];
        }

        if (leaf.Length == 0)
        {
            return "The request body is not valid JSON.";
        }

        return message.StartsWith(FieldMessagePrefix, StringComparison.Ordinal)
            ? $"{leaf} {message}"
            : $"{leaf} has an invalid value.";
    }
}
