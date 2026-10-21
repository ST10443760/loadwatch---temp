using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/users/{userId}/favourites")]
public class FavouritesController : ControllerBase
{
    // GET /api/users/{userId}/favourites (bearer)
    [HttpGet]
    public ActionResult<IEnumerable<FavouriteArea>> GetFavourites(string userId)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }

    // POST /api/users/{userId}/favourites (bearer) -> 201 FavouriteArea
    [HttpPost]
    public ActionResult<FavouriteArea> AddFavourite(string userId, [FromBody] FavouriteAreaCreate request)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }

    // DELETE /api/users/{userId}/favourites/{areaId} (bearer) -> 204
    [HttpDelete("{areaId}")]
    public IActionResult RemoveFavourite(string userId, string areaId)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }
}
