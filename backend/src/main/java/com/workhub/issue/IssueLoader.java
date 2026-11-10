package com.workhub.issue;

import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Finds an issue together with the caller's access to its project; answers 404 for anything they may not see. */
@Component
class IssueLoader {

	record Loaded(Issue issue, ProjectAccess access) {
	}

	private final IssueRepository issues;

	private final AccessService access;

	IssueLoader(IssueRepository issues, AccessService access) {
		this.issues = issues;
		this.access = access;
	}

	Loaded load(UUID userId, UUID issueId) {
		Issue issue = issues.findById(issueId).orElseThrow(IssueLoader::notFound);
		try {
			return new Loaded(issue, access.project(userId, issue.getProjectId()));
		}
		catch (ApiException ex) {
			// Do not reveal that the issue exists in a project the caller cannot see.
			throw (ex.getStatus() == HttpStatus.NOT_FOUND) ? notFound() : ex;
		}
	}

	static ApiException notFound() {
		return ApiException.notFound("ISSUE_NOT_FOUND", "Issue not found");
	}

}
