# Dockerfile for Lightning Engine GUI
# Note: This requires X11 display forwarding or virtual framebuffer for headless mode
#
# For X11 forwarding on Linux:
#   docker run -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix lightning-gui
#
# For headless testing (uses Xvfb):
#   docker run -e HEADLESS=true lightning-gui

FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom files for dependency caching
COPY pom.xml ./
COPY utils/pom.xml ./utils/
COPY lightning-engine/engine-core/pom.xml ./lightning-engine/engine-core/
COPY lightning-engine/engine-internal/pom.xml ./lightning-engine/engine-internal/
COPY lightning-engine/rendering-core/pom.xml ./lightning-engine/rendering-core/
COPY lightning-engine/rendering-test/pom.xml ./lightning-engine/rendering-test/
COPY lightning-engine/webservice/web-api-adapter/pom.xml ./lightning-engine/webservice/web-api-adapter/
COPY lightning-engine/gui/pom.xml ./lightning-engine/gui/

# Download dependencies
RUN mvn dependency:go-offline -B -pl lightning-engine/gui -am || true

# Copy source code
COPY utils/src ./utils/src
COPY lightning-engine/engine-core/src ./lightning-engine/engine-core/src
COPY lightning-engine/engine-internal/src ./lightning-engine/engine-internal/src
COPY lightning-engine/rendering-core/src ./lightning-engine/rendering-core/src
COPY lightning-engine/rendering-test/src ./lightning-engine/rendering-test/src
COPY lightning-engine/webservice/web-api-adapter/src ./lightning-engine/webservice/web-api-adapter/src
COPY lightning-engine/gui/src ./lightning-engine/gui/src

# Build the GUI module
RUN mvn package -B -DskipTests -pl lightning-engine/gui -am

# Runtime stage with OpenGL support
FROM eclipse-temurin:21-jdk

# Install X11, OpenGL, and Xvfb for headless rendering
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libgl1-mesa-dri \
    libxrender1 \
    libxtst6 \
    libxi6 \
    xvfb \
    x11-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR and dependencies
COPY --from=build /app/lightning-engine/gui/target/engine-gui-*.jar ./engine-gui.jar
COPY --from=build /app/lightning-engine/gui/target/dependency/ ./lib/

# Create startup script
RUN echo '#!/bin/bash\n\
if [ "$HEADLESS" = "true" ]; then\n\
    Xvfb :99 -screen 0 1024x768x24 &\n\
    export DISPLAY=:99\n\
    sleep 1\n\
fi\n\
java -cp "engine-gui.jar:lib/*" com.lightningfirefly.engine.gui.EngineGuiApplication "$@"\n\
' > /app/start.sh && chmod +x /app/start.sh

# Default backend URL
ENV BACKEND_URL=http://host.docker.internal:8080
ENV HEADLESS=false

ENTRYPOINT ["/app/start.sh"]
CMD ["-s", "http://host.docker.internal:8080", "-m", "1"]
