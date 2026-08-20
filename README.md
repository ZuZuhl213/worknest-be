# WorkNest Backend

Dịch vụ Backend cho WorkNest xây dựng trên Spring Boot 3.5 và Java 25.

---

## Tính năng chính

- **Xác thực & Phân quyền**: JWT authentication qua HTTP-only cookies, OAuth2 Google login, RBAC granular permissions.
- **Quản lý Workspace & Project**: CRUD workspaces, project memberships, custom roles, activity logging.
- **Quản lý Task & Kanban Board**: Task workflows, tags, assignments, audit logs, comments, due date reminders.
- **File Storage**: Tích hợp S3 / MinIO lưu trữ tệp đính kèm và tối ưu ảnh WebP.
- **Background Jobs**: Quartz scheduler định kỳ kiểm tra task quá hạn và dọn dẹp storage.
- **Bảo mật & Vận hành**: Flyway schema migration, Redis rate limiting, health check endpoints (`/ops/health`).

---

## Yêu cầu

- **Java**: 25 (OpenJDK / Temurin)
- **Docker & Docker Compose**

---

## Cài đặt & Khởi chạy

1. **Thiết lập biến môi trường:**
   ```bash
   cp .env.example .env
   ```

2. **Khởi động các dịch vụ phụ trợ (Postgres, Redis, Mailpit, MinIO):**
   ```bash
   ./scripts/local-up.sh
   # Hoặc: docker compose up -d postgres redis mailpit minio minio-init
   ```

3. **Chạy ứng dụng:**
   ```bash
   ./mvnw spring-boot:run
   ```

- **API Base URL**: `http://localhost:8000`
- **Swagger UI**: `http://localhost:8000/swagger-ui.html`
- **Health check**: `http://localhost:8000/ops/health`
- **Mailpit Web UI (Mail inbox local)**: `http://localhost:8025`
- **MinIO Console (S3 storage UI)**: `http://localhost:9001` (`minioadmin` / `minioadmin123`)

---

## Kiểm thử & Đóng gói

```bash
# Chạy Unit & Integration tests
./mvnw test

# Đóng gói file JAR
./mvnw clean package -DskipTests
```
