using System.Net.Sockets;
using Microsoft.Data.SqlClient;

namespace LoadWatch.Api.Data;

// Decides whether a database failure is worth retrying. Transient means the server is waking up, busy or
// briefly unreachable; a bad password, a missing table or a constraint violation is permanent.
public static class TransientSqlErrors
{
    // SQL error numbers Azure SQL returns while a serverless database resumes, fails over or is throttled,
    // plus the network-level numbers SqlClient reports when a connection drops
    public static readonly IReadOnlySet<int> Numbers = new HashSet<int>
    {
        40613, // Database is not currently available (paused, resuming or failing over)
        40197, // The service encountered an error processing the request
        40501, // The service is currently busy
        49918, // Not enough resources to process the request
        49919, // Too many create or update operations in progress
        49920, // Too many operations in progress
        4060,  // Cannot open the database requested by the login (often while it resumes)
        4221,  // Login to a read-secondary failed during a reconfiguration
        40143, // The service encountered an error while processing the request
        40540, // The service encountered an error while processing the request
        10928, // Resource limit reached
        10929, // Resource limit reached
        10053, // Connection aborted by the host
        10054, // Connection reset by the remote host
        10060, // Connection attempt timed out
        233,   // No process is on the other end of the pipe (connection closed during login)
        121,   // Semaphore timeout on the network
        64,    // The specified network name is no longer available
        -2,    // Timeout expired
    };

    public static bool IsTransientNumber(int number) => Numbers.Contains(number);

    // Checks the exception and everything it wraps, because EF Core and SqlClient both nest the real cause
    public static bool IsTransient(Exception exception)
    {
        for (var current = exception; current is not null; current = current.InnerException)
        {
            switch (current)
            {
                case SqlException sql when sql.Errors.Cast<SqlError>().Any(e => IsTransientNumber(e.Number)) || IsTransientNumber(sql.Number):
                    return true;
                case SocketException socket when socket.SocketErrorCode is
                    SocketError.ConnectionReset or SocketError.ConnectionAborted or SocketError.TimedOut:
                    return true;
                case TimeoutException:
                    return true;
            }
        }
        return false;
    }
}
