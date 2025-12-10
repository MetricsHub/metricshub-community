#!/bin/sh

set -e

copy_defaults() {
    TARGET_DIR="$1"
    DEFAULT_DIR="$2"

    if [ -d "$TARGET_DIR" ] && [ -z "$(ls -A $TARGET_DIR 2>/dev/null)" ]; then
        echo "Directory $TARGET_DIR is empty. Copying default configuration from $DEFAULT_DIR..."
        cp -r "$DEFAULT_DIR"/* "$TARGET_DIR"/
    else
        echo "Directory $TARGET_DIR has user content."
    fi
}

add_default_config() {
    TARGET_FILE="$1"
    DEFAULT_FILE="$2"

    if [ ! -f "$TARGET_FILE" ]; then
        echo "File $TARGET_FILE does not exist. Copying default configuration from $DEFAULT_FILE..."
        cp "$DEFAULT_FILE" "$TARGET_FILE"
    else
        echo "File $TARGET_FILE already exists."
    fi
}

copy_defaults "/opt/metricshub/lib/config" "/opt/metricshub/defaults/config"

add_default_config "/opt/metricshub/lib/config/metricshub.yaml" "/opt/metricshub/defaults/config/metricshub-example.yaml"

exec "$@"