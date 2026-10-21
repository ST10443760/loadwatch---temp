using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Data;

public static class DbUpdateExceptionExtensions
{
    // SQL Server error numbers for a duplicate key in a unique index or primary key
    private const int UniqueIndexViolation = 2601;
    private const int UniqueConstraintViolation = 2627;

    public static bool IsUniqueViolation(this DbUpdateException exception) =>
        exception.InnerException is SqlException { Number: UniqueIndexViolation or UniqueConstraintViolation };
}
