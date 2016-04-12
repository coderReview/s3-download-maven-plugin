package com.upplication.maven.plugins.s3.download;

import org.junit.Assert;
import org.junit.Test;

public class S3DownloadMojoTest {

	@Test
	public void exclude() {
		S3DownloadMojo testee = new S3DownloadMojo();
		testee.setExclude(".mdl");
		Assert.assertTrue(testee.isExclude("aa/bb/cc/test.mdl"));
		Assert.assertTrue(testee.isExclude("anothertest.mdl"));
	}

	@Test
	public void dontExclude() {
		S3DownloadMojo testee = new S3DownloadMojo();
		testee.setExclude(".mdl");
		Assert.assertFalse(testee.isExclude("aa/bb/cc/test.mmdl"));
		Assert.assertFalse(testee.isExclude("aa/bb/cc/"));
	}

}
