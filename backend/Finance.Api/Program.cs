var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseHttpsRedirection();

app.MapGet("/api/health", () => Results.Ok(new
{
    status = "ok",
    service = "Finance.Api"
}))
.WithName("GetHealth")
.WithSummary("Checks that the FinanceApp backend is responding.");

app.Run();
