package com.upplication.maven.plugins.s3.download;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

// this defines the goal
@Mojo(name = "s3-download")
public class S3DownloadMojo extends AbstractMojo {

    /**
     * Access key for S3.
     */
    @Parameter(property = "s3-download.accessKey", required = true)
    private String accessKey;

    /**
     * Secret key for S3.
     */
    @Parameter(property = "s3-download.secretKey", required = true)
    private String secretKey;

    /**
     * The file/folder to download.
     */
    @Parameter(property = "s3-download.source")
    private String source;

    /**
     * If source is directory then copy into destination relative
     * to the source (do not recreate the S3 full key under destination)
     */
    @Parameter(property = "s3-download.relative")
    private boolean relative = false;

    /**
     * Optional if provided, exclude files with key after final delimiter matching
     * this pattern
     * xxx/yyy/zzz/test.mdl is excluded if string is ".mdl"
     */
    @Parameter(property = "s3-download.exclude")
    private String exclude = null;

    /**
     * The bucket to download from.
     */
    @Parameter(property = "s3-download.bucketName", required = true)
    private String bucketName;

    /**
     * The file/folder to create (it will be treated as a directory ONLY if it ends with a /)
     */
    @Parameter(property = "s3-download.destination", required = true)
    private String destination;

    /**
     * Force override of endpoint for S3 regions such as EU.
     */
    @Parameter(property = "s3-download.endpoint")
    private String endpoint;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("--- s3-download-maven-plugin");
        getLog().info(String.format("Bucket: %s, source: %s, destination: %s relative:\n", bucketName, source, destination, relative));
        
        if (source == null) {
            source = "";
        }
        
        AmazonS3 s3 = getS3Client(accessKey, secretKey);
        if (endpoint != null) {
            s3.setEndpoint(endpoint);
        }

        if (!s3.doesBucketExist(bucketName)) {
            throw new MojoExecutionException("Bucket doesn't exist: " + bucketName);
        }

        try {
            download(s3);
        } catch (IOException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to download file from S3.");
        }

        getLog().info("Successfully downloaded all files");
    }

    /**
     * Retrieve a new brand S3 client with the given access and secret keys
     *
     * @param accessKey Access key
     * @param secretKey Secret key
     * @return Amazon S3 client
     */
    private static AmazonS3 getS3Client(String accessKey, String secretKey) {
        AWSCredentialsProvider provider;
        if (accessKey != null && secretKey != null) {
            AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
            provider = new StaticCredentialsProvider(credentials);
        } else {
            provider = new DefaultAWSCredentialsProviderChain();
        }

        return new AmazonS3Client(provider);
    }

    /**
     * Given a file path it will te if it's a directory or not. That is, if it ends with a slash ("/")
     *
     * @param path File path
     * @return Is it a directory?
     */
    private static boolean isDirectory(String path) {
        return path.charAt(path.length() - 1) == '/';
    }

    /**
     * Download a file from Amazon S3
     *
     * @param s3 Amazon S3 client
     * @throws MojoExecutionException
     */
    private void download(AmazonS3 s3) throws MojoExecutionException, IOException {
        File destinationFile = new File(destination);
        if (isDirectory(destination)) {
            destinationFile.mkdirs();
        }

        // If the destination file is a directory just list the objects in the source and download them
        // recursively
        if (destinationFile.isDirectory()) {
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                    .withBucketName(bucketName)
                    .withPrefix(source);
            ObjectListing objectListing;

            do {
                objectListing = s3.listObjects(listObjectsRequest);
                for (S3ObjectSummary objectSummary :
                        objectListing.getObjectSummaries()) {
                    downloadSingleFile(s3, destinationFile, objectSummary.getKey());
                }

                listObjectsRequest.setMarker(objectListing.getNextMarker());
            } while (objectListing.isTruncated());
        } else {
            downloadSingleFile(s3, destinationFile, source);
        }
    }
    
    private String getRelativeFromKey(String key) {
    	if (relative && key.length() > source.length()) {
    		return key.substring(source.length());
    	}
    	// return original otherwise
    	return key;
    }

    /**
     * Download a single file. If the key is a directory it will only create the directory on your filesystem.
     *
     * @param s3          Amazon S3 client
     * @param destination Destination path
     * @param key         Amazon S3 key
     * @throws IOException
     */
    private void downloadSingleFile(AmazonS3 s3, File destination, String key) throws IOException {
        getLog().debug(String.format("Downloading %s", key));
        if (!isExclude(key)) {
            File newDestination = destination.toPath().resolve(getRelativeFromKey(key)).toFile();

            if (isDirectory(key)) {
                newDestination.mkdirs();
            } else {
                newDestination.getParentFile().mkdirs();

                GetObjectRequest request = new GetObjectRequest(bucketName, key);
                S3Object object = s3.getObject(request);
                S3ObjectInputStream objectContent = object.getObjectContent();

                Files.copy(objectContent, newDestination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            getLog().debug(String.format("Excluding %s", key));
        }
    }
    
    protected void setExclude(String excluded) {
    	this.exclude = excluded;
    }
    protected boolean isExclude(String key) {
    	getLog().debug(String.format("isExclude() key:%s  exclude:%s", key, exclude));
    	if (!StringUtils.isBlank(exclude) && !StringUtils.isBlank(key) && key.length() > exclude.length()) {
    		return (StringUtils.substring(key, key.length()-exclude.length()).equals(exclude));
    	}
    	return false;
    }

}
