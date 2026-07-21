#!/usr/bin/env bash
# Generates every icon asset for Signboard from scratch.
#
#   ./branding/generate-icons.sh
#
# Outputs:
#   - branding/icon-512.png            Play Store listing icon (512x512, PNG32, full bleed)
#   - src/main/res/mipmap-*/ic_launcher.png   Launcher icons, all densities
#
# Design: black top half with "abc" in white, white bottom half with "ABC" in black.
# Each word's optical center sits exactly at 25% / 75% of the icon height, so the
# two words are centered inside their own half.
#
# Why the trim-then-composite dance: ImageMagick's `-annotate` positions text by
# its font baseline and em box, not by the ink it actually puts on the canvas.
# Rendering to a transparent canvas, trimming to the ink bounds, then compositing
# at a computed offset is the only way to center reliably across sizes and glyph sets.
#
# Play Store spec (https://developer.android.com/distribute/google-play/resources/icon-design-specifications):
#   512x512, 32-bit PNG, sRGB, full square, NO rounded corners, NO drop shadow.
#   Play applies a 30% corner radius and shadow itself, so the artwork must not.
#   Corner masking means artwork near the corners gets cut: we keep the text within
#   ~60% of the width, comfortably inside the keyline grid.

set -euo pipefail

cd "$(dirname "$0")/.."

FONT="/System/Library/Fonts/Helvetica.ttc"
TEXT_WIDTH_RATIO=0.60 # target width of "ABC" relative to icon width, keeps text off the masked corners
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# render_icon <size> <output-path>
render_icon() {
  local size=$1 out=$2
  local half=$((size / 2))

  # Point size chosen so "ABC" lands at TEXT_WIDTH_RATIO of the icon width.
  # Helvetica caps run ~2.1x the point size for three letters; we correct below anyway.
  local pointsize
  pointsize=$(awk -v s="$size" -v r="$TEXT_WIDTH_RATIO" 'BEGIN { printf "%d", s * r / 2.1 }')

  magick -size "${size}x${size}" xc:transparent -font "$FONT" -pointsize "$pointsize" \
    -fill white -gravity Center -annotate +0+0 "abc" -trim +repage "PNG32:$TMP/top.png"
  magick -size "${size}x${size}" xc:transparent -font "$FONT" -pointsize "$pointsize" \
    -fill black -gravity Center -annotate +0+0 "ABC" -trim +repage "PNG32:$TMP/bottom.png"

  # Correct for the font-metric guess: scale both words by the same factor so "ABC"
  # hits the target width exactly. Same factor for both keeps their relative weight.
  local abc_w scale
  abc_w=$(magick identify -format '%w' "$TMP/bottom.png")
  scale=$(awk -v s="$size" -v r="$TEXT_WIDTH_RATIO" -v w="$abc_w" 'BEGIN { printf "%.4f", (s * r) / w }')
  magick "$TMP/top.png" -resize "$(awk -v s="$scale" 'BEGIN { printf "%.2f", s * 100 }')%" "PNG32:$TMP/top.png"
  magick "$TMP/bottom.png" -resize "$(awk -v s="$scale" 'BEGIN { printf "%.2f", s * 100 }')%" "PNG32:$TMP/bottom.png"

  # Place each word's ink box centered on the 25% / 75% horizontal lines.
  local tw th bw bh tx ty bx by
  tw=$(magick identify -format '%w' "$TMP/top.png")
  th=$(magick identify -format '%h' "$TMP/top.png")
  bw=$(magick identify -format '%w' "$TMP/bottom.png")
  bh=$(magick identify -format '%h' "$TMP/bottom.png")
  tx=$(((size - tw) / 2))
  ty=$((half / 2 - th / 2))
  bx=$(((size - bw) / 2))
  by=$((half + half / 2 - bh / 2))

  magick -size "${size}x${size}" xc:black \
    \( -size "${size}x${half}" xc:white \) -gravity South -composite \
    "$TMP/top.png" -gravity NorthWest -geometry "+${tx}+${ty}" -composite \
    "$TMP/bottom.png" -gravity NorthWest -geometry "+${bx}+${by}" -composite \
    -colorspace sRGB -background black -alpha remove -alpha on \
    "PNG32:$out"
}

echo "Play Store icon:"
render_icon 512 branding/icon-512.png
echo "  branding/icon-512.png"

echo "Launcher icons:"
for entry in ldpi:36 mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  density=${entry%%:*}
  size=${entry##*:}
  dir="src/main/res/mipmap-${density}"
  mkdir -p "$dir"
  render_icon "$size" "$dir/ic_launcher.png"

  # These ship inside the APK, where they're a real fraction of the total size, so store
  # them as true 8-bit grayscale rather than 32-bit RGBA. The artwork is black, white, and
  # the gray of antialiased edges: no color, no transparency.
  #
  # Grayscale, NOT `PNG8:` palette. Quantizing the RGBA render to a palette saves ~1.3 KB
  # per icon but is lossy on the antialiased glyph edges, and it shifted the white half to
  # 250,250,250. Not worth it. (The Play Store icon above stays PNG32; its spec demands 32-bit.)
  magick "$dir/ic_launcher.png" -strip -colorspace Gray \
    -define png:compression-level=9 "$dir/ic_launcher.png"

  echo "  $dir/ic_launcher.png (${size}x${size}, $(wc -c < "$dir/ic_launcher.png" | tr -d ' ') bytes)"
done
