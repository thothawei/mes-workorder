# ---- 建置階段 ----
# 用 JDK 17 建置：Lombok 的 annotation processing 在較新的 JDK 上會靜默失效，
# 釘死版本可省下一輪莫名其妙的 cannot find symbol。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只複製 pom.xml 下載依賴，這一層在原始碼改動時可以重用快取，
# 每次改一行程式碼就重抓整個 Maven repository 是很浪費的。
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# 映像檔建置時跳過測試：測試需要 Docker（Testcontainers），
# 而 Docker in Docker 不是這裡該處理的問題。測試由 CI 或本機 mvn verify 負責。
RUN mvn -B clean package -DskipTests

# ---- 執行階段 ----
# JRE 而非 JDK，映像檔小一半。
#
# 用 jammy 而不是 alpine：Temurin 的 alpine 變體只發布 amd64，
# 在 Apple Silicon（arm64）上會失敗於「no match for platform in manifest」。
# 為了省那 100MB 而讓一半的開發者 build 不起來並不划算。
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 不以 root 執行（Debian/Ubuntu 基底用 groupadd/useradd，不是 alpine 的 addgroup/adduser）
RUN groupadd -r mes && useradd -r -g mes mes
USER mes

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# 容器環境下讓 JVM 依實際配額決定 heap，而不是看到整台主機的記憶體
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
