package org.matsim.mosaik2.analysis;

import lombok.RequiredArgsConstructor;

import java.nio.file.Path;

@RequiredArgsConstructor
public class CalculateLinkExposure {

	private final Path exposureFile;
	private final Path rValueFile;
	private final Path networkFile;


}