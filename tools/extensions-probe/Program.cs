using System.Text.Json;
using System.Net;
using System.Net.Sockets;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        using var form = new Form { Width = 800, Height = 700, Opacity = 0, ShowInTaskbar = false };
        using var site = new WebView2 { Dock = DockStyle.Fill };
        using var popup = new WebView2 { Dock = DockStyle.Fill };
        form.Controls.Add(site);
        form.Controls.Add(popup);
        form.Shown += async (_, _) =>
        {
            try
            {
                using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(50));
                await Run(site, popup, args).WaitAsync(timeout.Token);
            }
            catch (Exception error)
            {
                Console.WriteLine(error);
                Environment.ExitCode = 1;
            }
            finally
            {
                form.Close();
            }
        };
        Application.Run(form);
    }

    private static async Task Run(WebView2 site, WebView2 popup, string[] args)
    {
        var root = Path.GetFullPath("build/extensions-probe-data");
        Directory.CreateDirectory(root);
        var options = new CoreWebView2EnvironmentOptions { AreBrowserExtensionsEnabled = true };
        var environment = await CoreWebView2Environment.CreateAsync(null,
            Path.Combine(root, "profile-" + Guid.NewGuid()), options);
        await site.EnsureCoreWebView2Async(environment);
        await popup.EnsureCoreWebView2Async(environment);
        Console.WriteLine("Runtime: " + environment.BrowserVersionString);
        var fixture = Path.Combine(root, "fixture");
        Directory.CreateDirectory(fixture);
        await File.WriteAllTextAsync(Path.Combine(fixture, "manifest.json"), """
            {"manifest_version":3,"name":"Spark extension probe","version":"1.0",
             "permissions":["storage","tabs","scripting","activeTab"],"host_permissions":["<all_urls>"],
             "action":{"default_popup":"popup.html"},"options_ui":{"page":"options.html","open_in_tab":true}}
            """);
        await File.WriteAllTextAsync(Path.Combine(fixture, "popup.html"), "<html><body>Extension popup</body></html>");
        await File.WriteAllTextAsync(Path.Combine(fixture, "options.html"), "<html><body>Extension options</body></html>");
        var portProbe = new TcpListener(IPAddress.Loopback, 0);
        portProbe.Start();
        var port = ((IPEndPoint)portProbe.LocalEndpoint).Port;
        portProbe.Stop();
        using var server = new HttpListener();
        server.Prefixes.Add("http://127.0.0.1:" + port + "/");
        server.Start();
        var serve = Task.Run(async () => {
            var request = await server.GetContextAsync();
            var body = System.Text.Encoding.UTF8.GetBytes("<html><title>Spark test site</title><body><input id='username'></body></html>");
            request.Response.ContentType = "text/html";
            await request.Response.OutputStream.WriteAsync(body);
            request.Response.Close();
        });
        await Navigate(site, "http://127.0.0.1:" + port + "/");
        await serve;
        var extension = await site.CoreWebView2.Profile.AddBrowserExtensionAsync(fixture);
        Console.WriteLine("Installed: " + extension.Name + " / " + extension.Id);
        await Navigate(popup, "chrome-extension://" + extension.Id + "/popup.html");
        Console.WriteLine("Popup: " + await popup.CoreWebView2.ExecuteScriptAsync(
            "JSON.stringify({id:chrome.runtime.id,storage:typeof chrome.storage.local.get,text:document.body.innerText})"));
        popup.CoreWebView2.WebMessageReceived += (_, message) => Console.WriteLine("PROBE " + message.WebMessageAsJson);
        await popup.CoreWebView2.ExecuteScriptAsync("""
            (async()=>{
              try {
                const tabs=await chrome.tabs.query({});
                chrome.webview.postMessage({tabs});
                chrome.webview.postMessage({active:await chrome.tabs.query({active:true,currentWindow:true})});
                const target=tabs.find(t=>t.title==='Spark test site');
                if(target){
                  const results=await chrome.scripting.executeScript({target:{tabId:target.id},func:()=>{document.querySelector('#username').value='filled';return location.href}});
                  chrome.webview.postMessage({results});
                }
              }catch(error){chrome.webview.postMessage({error:String(error)})}
            })()
            """);
        await Task.Delay(1000);
        var filled = await site.CoreWebView2.ExecuteScriptAsync("document.querySelector('#username').value");
        Console.WriteLine("Autofill: " + filled);
        if (filled != "\"filled\"") throw new Exception("Explicit-tab autofill failed");
        await popup.CoreWebView2.ExecuteScriptAsync("""
            (async()=>{
              try {await chrome.action.openPopup();chrome.webview.postMessage({nativeActionPopup:"opened"})}
              catch(error){chrome.webview.postMessage({nativeActionPopup:String(error)})}
            })()
            """);
        await Task.Delay(1000);
        await Navigate(popup, "chrome-extension://" + extension.Id + "/options.html");
        Console.WriteLine("Options: " + await popup.CoreWebView2.ExecuteScriptAsync("document.body.innerText"));
        if (args.Length > 0)
        {
            var directory = Path.GetFullPath(args[0]);
            using var manifest = JsonDocument.Parse(await File.ReadAllTextAsync(Path.Combine(directory, "manifest.json")));
            var installed = await site.CoreWebView2.Profile.AddBrowserExtensionAsync(directory);
            Console.WriteLine("Installed real extension: " + installed.Name + " / " + installed.Id);
            var page = manifest.RootElement.GetProperty("action").GetProperty("default_popup").GetString();
            await Navigate(popup, "chrome-extension://" + installed.Id + "/" + page);
            await Task.Delay(6000);
            Console.WriteLine("Real popup: " + await popup.CoreWebView2.ExecuteScriptAsync("document.body.innerText.slice(0,2500)"));
            await using var screenshot = File.Create(Path.Combine(root, "real-popup.png"));
            await popup.CoreWebView2.CapturePreviewAsync(CoreWebView2CapturePreviewImageFormat.Png, screenshot);
        }
        var extensions = await site.CoreWebView2.Profile.GetBrowserExtensionsAsync();
        Console.WriteLine("Extension count: " + extensions.Count);
    }

    private static async Task Navigate(WebView2 view, string url)
    {
        var loaded = new TaskCompletionSource();
        void Completed(object? sender, CoreWebView2NavigationCompletedEventArgs e)
        {
            if (e.IsSuccess) loaded.TrySetResult();
            else loaded.TrySetException(new Exception("Navigation failed: " + e.WebErrorStatus + " / " + url));
        }
        view.CoreWebView2.NavigationCompleted += Completed;
        try
        {
            view.CoreWebView2.Navigate(url);
            await loaded.Task.WaitAsync(TimeSpan.FromSeconds(15));
        }
        finally
        {
            view.CoreWebView2.NavigationCompleted -= Completed;
        }
    }
}
