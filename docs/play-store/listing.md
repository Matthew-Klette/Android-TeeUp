# Google Play Store listing: TeeUp

Everything needed to fill in the Play Console for the first release.

## App details

| Field | Value |
|---|---|
| App name (30 max) | TeeUp: Golf Tee Times & Groups |
| Package name | `com.teeup.android` |
| Version | 1.0.0 (version code 2) |
| Category | Sports |
| Tags | Golf, Sports, Social |
| Contact email | (team support address) |
| Privacy policy URL | Publish `privacy-policy.md` (for example with GitHub Pages) and paste its URL |
| Countries | South Africa |
| Price | Free, no ads, no in-app purchases |

## Short description (80 max)

Book tee times, fill your group with matched golfers and score live with GPS.

## Full description (4000 max)

TeeUp is the golf app for when you have a tee time but not a full four-ball.

FIND A GAME
Browse open groups at real South African courses and filter by date, number of players, course, holes, handicap range and pace of play. Request to join with one tap, and the host gets a push notification straight away.

HOST YOUR OWN GROUP
Create a group with the handicap range and pace you want. Accept or decline requests. Edit, cancel or delete the group at any time, and your players are notified instantly.

PLAYING PARTNERS
Save the golfers you play with. Make a group partners-only and just your partners can see and join it. Each of them gets an invite.

LIVE GPS SCORECARD
A satellite hole map shows live distances to the front, middle and back of the green. It follows you from hole to hole, and you can drag the layup target to plan your shot. Enter strokes and putts as you go. TeeUp works out your totals, net score and Stableford points from your handicap.

WORKS OFFLINE
No signal on the back nine? Keep scoring. Finished rounds are saved on your phone and upload on their own when you're back online.

SECURE AND PRIVATE
Sign in with Google or email, then unlock with your fingerprint or face. Your data is only ever sent over an encrypted connection.

IN YOUR LANGUAGE
Available in English, Afrikaans and isiXhosa.

## Graphics

| Asset | Requirement | File |
|---|---|---|
| App icon | 512 x 512 PNG, 32-bit | `icon-512.png` |
| Feature graphic | 1024 x 500 PNG/JPG | `feature-graphic.png` |
| Phone screenshots | 2 to 8, 16:9 or 9:16, 320 to 3840 px per side | `screenshots/` |

## Content rating questionnaire (IARC)

- Violence, sexual content, profanity, drugs, gambling: none.
- User interaction: yes. Users can see other golfers' display names, handicaps and pace of play, and send join requests. There is no free-text chat.
- Shares location: no. GPS is only used on the device for hole distances and is never sent to the server.
- Expected rating: Everyone / PEGI 3.

## Data safety form

| Data type | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Email address | Yes (Firebase Authentication) | No | Account management | No |
| Name (display name) | Yes | Shown to other golfers in groups | App functionality | No |
| Other info (handicap, home course, pace of play, rounds, scores) | Yes | Handicap/pace shown in groups | App functionality | Handicap and home course are optional |
| Photos | No. The profile photo stays on the device | No | n/a | Yes |
| Precise location | Used on device only, not collected | No | Hole distances | Yes |
| Device or other IDs (FCM token) | Yes | No | Push notifications | Yes (notification permission) |

- Data is encrypted in transit (HTTPS only, `usesCleartextTraffic=false`).
- Users can request account deletion through the support email (see the privacy policy).

## Release checklist

- [x] Signed release build (upload key, APK Signature Scheme v2), R8 shrinking on
- [x] Version code and name set (2 / 1.0.0)
- [x] Adaptive launcher icon, round icon, monochrome themed icon, notification icon
- [x] Target API 34, HTTPS only, no unused dangerous permissions requested at install
- [x] Store listing text, icon and feature graphic
- [x] Privacy policy written
- [ ] Phone screenshots captured on a physical device (see `screenshots/README.md`)
- [ ] Build the Android App Bundle for upload: `./gradlew bundleRelease` (output `app/build/outputs/bundle/release/app-release.aab`)
- [ ] Enrol in Play App Signing and upload the `.aab` to the internal testing track
