# Dot-source this file from the project root: . ./scripts/use-java21.ps1
$taskJdk = Get-ChildItem -Directory -LiteralPath (Join-Path $PSScriptRoot '../.tools') -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($taskJdk) {
    $env:JAVA_HOME = $taskJdk.FullName
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
java -version
