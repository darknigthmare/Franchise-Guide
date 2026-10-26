$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Version = '8.11.1'
$ExpectedSha256 = 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'
$Cache = Join-Path $Root '.gradle-bootstrap'
$Dist = Join-Path $Cache "gradle-$Version"
$Exe = Join-Path $Dist 'bin\gradle.bat'
$Zip = Join-Path $Cache "gradle-$Version-bin.zip"
if (!(Test-Path $Exe)) {
  New-Item -ItemType Directory -Force -Path $Cache | Out-Null
  $Url = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"
  Write-Host "Téléchargement de Gradle $Version depuis $Url"
  Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Zip
  if ((Get-FileHash -Algorithm SHA256 $Zip).Hash.ToLowerInvariant() -ne $ExpectedSha256) {
    Remove-Item $Zip -Force
    throw 'Empreinte Gradle incorrecte : archive refusée.'
  }
  Expand-Archive -Force -Path $Zip -DestinationPath $Cache
}
& $Exe -p $Root @args
exit $LASTEXITCODE
