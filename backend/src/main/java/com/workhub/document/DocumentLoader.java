package com.workhub.document;

import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Finds a document or folder with the caller's access to its project; unseen ones answer 404. */
@Component
class DocumentLoader {

	record Loaded(Document document, ProjectAccess access) {
	}

	record LoadedFolder(DocumentFolder folder, ProjectAccess access) {
	}

	private final DocumentRepository documents;

	private final DocumentFolderRepository folders;

	private final AccessService access;

	DocumentLoader(DocumentRepository documents, DocumentFolderRepository folders, AccessService access) {
		this.documents = documents;
		this.folders = folders;
		this.access = access;
	}

	Loaded load(UUID userId, UUID documentId) {
		Document document = documents.findById(documentId).orElseThrow(DocumentLoader::documentNotFound);
		return new Loaded(document, projectAccess(userId, document.getProjectId(), documentNotFound()));
	}

	LoadedFolder loadFolder(UUID userId, UUID folderId) {
		DocumentFolder folder = folders.findById(folderId).orElseThrow(DocumentLoader::folderNotFound);
		return new LoadedFolder(folder, projectAccess(userId, folder.getProjectId(), folderNotFound()));
	}

	private ProjectAccess projectAccess(UUID userId, UUID projectId, ApiException notFound) {
		try {
			return access.project(userId, projectId);
		}
		catch (ApiException ex) {
			throw (ex.getStatus() == HttpStatus.NOT_FOUND) ? notFound : ex;
		}
	}

	static ApiException documentNotFound() {
		return ApiException.notFound("DOCUMENT_NOT_FOUND", "Document not found");
	}

	static ApiException folderNotFound() {
		return ApiException.notFound("FOLDER_NOT_FOUND", "Folder not found");
	}

}
