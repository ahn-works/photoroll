param(
    [string]$RepoUrl
)

Write-Host "=== PhotoRoll Galaxy GitHub 1-Click 배포 스크립트 ===" -ForegroundColor Cyan

if (-not $RepoUrl) {
    $RepoUrl = Read-Host "깃허브(GitHub) 리포지토리 URL을 입력하세요 (예: https://github.com/내계정/photoroll.git)"
}

if (-not $RepoUrl) {
    Write-Host "오류: 리포지토리 URL이 입력되지 않았습니다." -ForegroundColor Red
    exit 1
}

Write-Host "GitHub 원격 저장소 연결 중: $RepoUrl" -ForegroundColor Yellow
git remote remove origin 2>$null
git remote add origin $RepoUrl
git branch -M main
git add .
git commit -m "feat: complete PhotoRoll Galaxy production release ready for Google Play Store" 2>$null

Write-Host "코드를 GitHub로 전송합니다..." -ForegroundColor Yellow
git push -u origin main --force

Write-Host "`n✅ GitHub 푸시 완료!" -ForegroundColor Green
Write-Host "이제 GitHub 저장소의 [Actions] 탭에서 Google Play Store용 .aab 번들이 자동으로 빌드됩니다!" -ForegroundColor Green
Write-Host "빌드가 끝나면 [Artifacts]에서 'PhotoRoll-PlayStore-Release-AAB'를 클릭해 다운로드하시면 됩니다." -ForegroundColor Cyan
