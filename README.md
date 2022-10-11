# Elastos.Carrier.Java

Elastos Carrier Super Node in Java

## Dependencies

- java ≥ 11
- libsoduium runtime (shared object)


## Build

```bash
git clone THIS_REPO_URL
cd REPO_DIR
./mvnw
```

If you want skip the test cases, use `./mvnw -Dmaven.test.skip=true` command instead of `./mvnw`.

If you want create a daily build, use 

```bash
./mvnw -Dmaven.test.skip=true -Drevision=6.0.0-"`date -u +"%Y%m%d%H%M"`"
```