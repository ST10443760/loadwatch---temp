using System.Globalization;
using Microsoft.AspNetCore.Mvc.ModelBinding;

namespace LoadWatch.Api.Json;

// Binds query and route instants as ISO-8601, treating a value with no offset (such as a bare date)
// as UTC, so a request means the same thing on a developer machine and on Azure.
public class UtcDateTimeOffsetModelBinder : IModelBinder
{
    public Task BindModelAsync(ModelBindingContext bindingContext)
    {
        var value = bindingContext.ValueProvider.GetValue(bindingContext.ModelName);
        if (value == ValueProviderResult.None || string.IsNullOrWhiteSpace(value.FirstValue))
        {
            return Task.CompletedTask;
        }

        bindingContext.ModelState.SetModelValue(bindingContext.ModelName, value);
        if (DateTimeOffset.TryParse(value.FirstValue, CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeUniversal, out var parsed))
        {
            bindingContext.Result = ModelBindingResult.Success(parsed);
        }
        else
        {
            bindingContext.ModelState.TryAddModelError(bindingContext.ModelName,
                $"'{value.FirstValue}' is not a valid ISO-8601 date or instant.");
        }

        return Task.CompletedTask;
    }
}

public class UtcDateTimeOffsetModelBinderProvider : IModelBinderProvider
{
    public IModelBinder? GetBinder(ModelBinderProviderContext context)
    {
        var type = Nullable.GetUnderlyingType(context.Metadata.ModelType) ?? context.Metadata.ModelType;
        return type == typeof(DateTimeOffset) && context.BindingInfo.BindingSource != BindingSource.Body
            ? new UtcDateTimeOffsetModelBinder()
            : null;
    }
}
