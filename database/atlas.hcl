env "local" {
  src = "file://schema"
  url = "postgres://chirpuser:chirppassword@database:5432/chirpdb?sslmode=disable"
  dev = "docker://postgres/18/dev?DOCKER_HOST=unix:///var/run/docker.sock"

  migration {
    dir = "file://migrations"
  }

  format {
    migrate {
      diff = "{{ sql . \"  \" }}"
    }
  }
}
