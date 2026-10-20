#!/usr/bin/env bash
# signing.properties の設定から署名鍵を作る。
# 使い方: scripts/create-signing-key.sh <app|upload> [設定ファイル]
set -euo pipefail

key="${1:-}"
config="${2:-$(dirname "$0")/../signing.properties}"

if [[ "$key" != "app" && "$key" != "upload" ]]; then
    echo "usage: $0 <app|upload> [signing.properties]" >&2
    exit 2
fi
if [[ ! -f "$config" ]]; then
    echo "$config がありません。signing.properties.example をコピーして作ってください。" >&2
    exit 1
fi
# パスワードを含むので、本人以外が読めるなら止める
mode=$(stat -c '%a' "$config" 2>/dev/null || stat -f '%Lp' "$config")
if [[ "${mode: -2}" != "00" ]]; then
    echo "$config は他のユーザーから読めます (mode $mode)。chmod 600 してください。" >&2
    exit 1
fi

# key=value 形式から 1 つ取り出す。値に = を含んでもよい。
prop() {
    local value
    value=$(sed -n "s/^${key}\.$1=//p" "$config" | tail -n 1)
    if [[ -z "$value" ]]; then
        echo "$config に ${key}.$1 がありません。" >&2
        exit 1
    fi
    printf '%s' "$value"
}

store_file=$(prop storeFile)
alias_name=$(prop keyAlias)
dname=$(prop dname)
validity=$(prop validityDays)
IROHA_SIGNING_PASSWORD=$(prop password)
export IROHA_SIGNING_PASSWORD

if [[ -e "$store_file" ]]; then
    echo "$store_file は既にあります。鍵を上書きすると更新できなくなるので、作り直すなら自分で退避してください。" >&2
    exit 1
fi
if [[ ${#IROHA_SIGNING_PASSWORD} -lt 6 ]]; then
    echo "${key}.password は 6 文字以上にしてください。" >&2
    exit 1
fi

mkdir -p "$(dirname "$store_file")"
# パスワードはコマンドラインに載せず環境変数で渡す（ps で見えないように）
keytool -genkeypair \
    -keystore "$store_file" -storetype PKCS12 \
    -storepass:env IROHA_SIGNING_PASSWORD \
    -alias "$alias_name" \
    -keyalg RSA -keysize 4096 -validity "$validity" \
    -dname "$dname"
chmod 600 "$store_file"

echo "作成しました: $store_file (alias: $alias_name)"
if [[ "$key" == "app" ]]; then
    echo "証明書の SHA-256（直接配布の README と F-Droid の AllowedAPKSigningKeys に載せる）:"
else
    echo "証明書の SHA-256（Play Console でアップロード鍵を登録するときに照合する）:"
fi
keytool -list -keystore "$store_file" -storepass:env IROHA_SIGNING_PASSWORD -alias "$alias_name" \
    | sed -n 's/.*SHA-256): //p' | tr -d ':' | tr 'A-F' 'a-f'
echo "鍵ファイルとパスワードは必ず 2 か所以上にバックアップしてください。"
