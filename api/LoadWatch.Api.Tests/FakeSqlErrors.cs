using System.Reflection;
using Microsoft.Data.SqlClient;

namespace LoadWatch.Api.Tests;

// Builds a SqlException with a chosen error number, such as 40613 from a paused Azure SQL database or 18456 from a
// wrong password, neither of which a local test server can produce on demand. SqlClient has no public
// constructor for these, so this goes through its internal factory methods.
public static class FakeSqlErrors
{
    private const BindingFlags Internal = BindingFlags.NonPublic | BindingFlags.Instance;

    public static SqlException Create(int number, string message = "Simulated SQL error")
    {
        var errorConstructor = typeof(SqlError).GetConstructors(Internal)
            .Where(c => c.GetParameters() is [{ ParameterType: var first }, ..] && first == typeof(int))
            .OrderByDescending(c => c.GetParameters().Length)
            .First();
        var arguments = errorConstructor.GetParameters()
            .Select((parameter, index) => index == 0 ? number : DefaultFor(parameter, message))
            .ToArray();
        var error = (SqlError)errorConstructor.Invoke(arguments);

        var errors = (SqlErrorCollection)Activator.CreateInstance(typeof(SqlErrorCollection), nonPublic: true)!;
        typeof(SqlErrorCollection).GetMethod("Add", Internal)!.Invoke(errors, [error]);

        var create = typeof(SqlException).GetMethod(
            "CreateException", BindingFlags.NonPublic | BindingFlags.Static, [typeof(SqlErrorCollection), typeof(string)])!;
        return (SqlException)create.Invoke(null, [errors, "16.0.0"])!;
    }

    private static object? DefaultFor(ParameterInfo parameter, string message) => parameter.ParameterType switch
    {
        var t when t == typeof(string) => parameter.Name == "errorMessage" ? message : "fake",
        var t when t == typeof(byte) => (byte)0,
        var t when t == typeof(int) => 0,
        var t when t == typeof(uint) => 0u,
        _ => null,
    };
}
