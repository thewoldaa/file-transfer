using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Windows;
using System.Windows.Controls;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;

namespace FileTransferServer;

public partial class MainWindow : Window
{
    private WebApplication? _app;
    private readonly ObservableCollection<FileItem> _files = [];
    private readonly string _savePath = @"K:\";
    private CancellationTokenSource? _cts;

    public MainWindow()
    {
        InitializeComponent();
        FileList.ItemsSource = _files;
        Loaded += OnLoaded;
        Closing += OnClosing;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        Directory.CreateDirectory(_savePath);
        var ip = GetLocalIp();
        IpText.Text = $"IP: {ip} :5000";
        await StartServer();
    }

    private string GetLocalIp()
    {
        try
        {
            var host = Dns.GetHostEntry(Dns.GetHostName());
            var ip = host.AddressList.FirstOrDefault(a => a.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(a));
            return ip?.ToString() ?? "127.0.0.1";
        }
        catch { return "127.0.0.1"; }
    }

    private async Task StartServer()
    {
        await Task.Yield();
        _cts = new CancellationTokenSource();
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseUrls("http://0.0.0.0:5000");
        builder.Logging.ClearProviders();

        _app = builder.Build();

        _app.Use(async (ctx, next) =>
        {
            ctx.Response.Headers["Access-Control-Allow-Origin"] = "*";
            ctx.Response.Headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS";
            ctx.Response.Headers["Access-Control-Allow-Headers"] = "*";
            if (ctx.Request.Method == "OPTIONS") { ctx.Response.StatusCode = 204; return; }
            await next();
        });

        _app.MapGet("/", async ctx =>
        {
            ctx.Response.ContentType = "text/html";
            await ctx.Response.WriteAsync("""
            <html><head><title>FileTransferServer</title>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{font-family:sans-serif;text-align:center;padding:40px;background:#1a1a2e;color:#eee}
            h1{color:#0078D4}.info{color:#aaa}</style></head><body>
            <h1>File Transfer Server</h1>
            <p class="info">Send files via POST /upload with multipart/form-data</p>
            </body></html>
            """);
        });

        _app.MapPost("/upload", async ctx =>
        {
            if (!ctx.Request.HasFormContentType)
            {
                ctx.Response.StatusCode = 400;
                await ctx.Response.WriteAsync("Bad Request");
                return;
            }

            var form = await ctx.Request.ReadFormAsync();
            foreach (var file in form.Files)
            {
                if (file.Length == 0) continue;
                var timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss_fff");
                var fileName = $"{timestamp}_{file.FileName}";
                var filePath = Path.Combine(_savePath, fileName);

                var fi = new FileItem
                {
                    FileName = file.FileName,
                    Size = FormatSize(file.Length),
                    Status = "Receiving...",
                    Time = DateTime.Now.ToString("HH:mm:ss"),
                    Progress = 0
                };
                Dispatcher.Invoke(() => _files.Insert(0, fi));

                try
                {
                    await using var fs = new FileStream(filePath, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true);
                    await using var stream = file.OpenReadStream();
                    var buffer = new byte[65536];
                    long totalRead = 0;
                    int bytesRead;
                    while ((bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length)) > 0)
                    {
                        await fs.WriteAsync(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        var progress = (int)(totalRead * 100 / file.Length);
                        Dispatcher.Invoke(() =>
                        {
                            fi.Progress = progress;
                            MainProgress.Value = progress;
                        });
                    }

                    fi.Status = "Completed";
                    fi.Progress = 100;
                    Dispatcher.Invoke(() =>
                    {
                        CountText.Text = $"{_files.Count} files";
                        FooterText.Text = $"Saved: {filePath}";
                        MainProgress.Visibility = Visibility.Collapsed;
                    });
                }
                catch (Exception ex)
                {
                    fi.Status = $"Error: {ex.Message}";
                    Dispatcher.Invoke(() => FooterText.Text = $"Error: {ex.Message}");
                }
            }
            await ctx.Response.WriteAsync("OK");
        });

        StatusText.Text = "Server Running";
        FooterText.Text = $"Listening on port 5000...";

        _ = Task.Run(() => _app.RunAsync());
    }

    private async void ToggleServer(object sender, RoutedEventArgs e)
    {
        if (_app != null)
        {
            _cts?.Cancel();
            await _app.StopAsync();
            _app = null;
            StatusText.Text = "Server Stopped";
            ToggleBtn.Content = "Start Server";
            FooterText.Text = "Server stopped";
        }
        else
        {
            await StartServer();
            ToggleBtn.Content = "Stop Server";
        }
    }

    private async void OnClosing(object? sender, CancelEventArgs e)
    {
        _cts?.Cancel();
        if (_app != null) await _app.StopAsync();
    }

    private static string FormatSize(long bytes)
    {
        string[] sizes = ["B", "KB", "MB", "GB"];
        int i = 0; double d = bytes;
        while (d >= 1024 && i < 3) { d /= 1024; i++; }
        return $"{d:F1} {sizes[i]}";
    }
}

public class FileItem : INotifyPropertyChanged
{
    private int _progress;
    public string FileName { get; set; } = "";
    public string Size { get; set; } = "";
    public string Status { get; set; } = "";
    public string Time { get; set; } = "";
    public int Progress
    {
        get => _progress;
        set { _progress = value; OnPropertyChanged(); }
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    protected void OnPropertyChanged([System.Runtime.CompilerServices.CallerMemberName] string? name = null)
        => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
