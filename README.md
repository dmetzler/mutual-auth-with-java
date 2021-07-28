# Mutual Authentication With Java

This is a repository to illustrate the blog post in my [Blog](https://dmetzler.github.io/)


## Generate certificates

Follow the [blog posts](https://dmetzler.github.io/mutual-auth-with-java-part1/) to understand about certificate generation. Here is a list of commands to generate the certs, start Nginx and run the Java test

```
mkdir -p certs
PASSWORD=changeit

# Generates the CA key and certificate
openssl req -nodes -x509 -newkey rsa:4096 -keyout certs/ca.key -out certs/ca.crt -days 365 -subj "/CN=my-own-ca"

# Generates the client key and signe it using the CA
openssl genrsa -out certs/client.key 4096
openssl req -new -key certs/client.key -out certs/client.csr -subj "/CN=my-identity" -sha256
openssl x509 -req -in certs/client.csr -CA certs/ca.crt -CAkey certs/ca.key -CAcreateserial -out certs/client.crt -days 1024

# Generates the server certificate
mkcert -cert-file certs/nginx.local.pem -key-file certs/nginx.local-key.pem nginx.local

# Runs the NGinx server 
docker run --name nginx -v $(pwd)/www:/var/www -v $(pwd)/nginx.conf:/etc/nginx/nginx.conf:ro -v $(pwd)/certs:/etc/nginx/certs -p 80:80 -p 443:443 -d nginx


curl --cert certs/client.crt --key certs/client.key  https://nginx.local


# Start assembling files for the Java tests
cp "$(mkcert -CAROOT)/rootCa.pem" ./java-client/src/test/resources/ca.crt
cat certs/ca.crt >> ./java-client/src/test/resources/ca.crt

# Converting key and cert in suitable format for Java
openssl pkcs8 -topk8 -inform PEM -in certs/client.key -out certs/client.pk8 -nocrypt
openssl pkcs12 -export -in certs/client.crt -inkey certs/client.key -out certs/client.p12 -name client -CAfile certs/ca.crt -caname my-own-ca -passout pass:$PASSWORD
cp certs/client.pk8 certs/client.crt ./java-client/src/test/resources

# Create client.jks
keytool -importkeystore -deststorepass $PASSWORD -destkeypass $PASSWORD -destkeystore certs/client.jks -srckeystore certs/client.p12 -srcstoretype PKCS12 -srcstorepass $PASSWORD -alias client
openssl x509 -outform der -in certs/ca.crt  -out certs/ca.der
keytool -import -noprompt -alias clientca -deststorepass $PASSWORD -keystore certs/client.jks -file certs/ca.der
openssl x509 -outform der -in "$(mkcert -CAROOT)/rootCa.pem"  -out certs/server.der
keytool -import -noprompt -alias serverca -deststorepass $PASSWORD -keystore certs/client.jks -file certs/server.der
cp certs/client.jks ./java-client/src/test/resources

# Run Java test
mvn clean package -f java-client/pom.xml

# Stop Nginx
docker rm -f nginx
```

## Licensing
Source code is licensed under the Apache License, Version 2.0.

See the LICENSE file and the documentation page Licenses for details.