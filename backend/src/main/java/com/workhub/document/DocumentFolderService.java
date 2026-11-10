package com.workhub.document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.workhub.access.AccessService;
import com.workhub.access.ProjectAccess;
import com.workhub.access.ProjectPermission;
import com.workhub.common.ApiException;
import com.workhub.document.DocumentLoader.LoadedFolder;
import com.workhub.document.DocumentResponses.FolderResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Document folders: a tree of at most {@value #MAX_DEPTH} levels per project; a folder is deleted only when empty. */
@Service
@Transactional
public class DocumentFolderService {

	static final int MAX_DEPTH = 4;

	private final AccessService access;

	private final DocumentLoader loader;

	private final DocumentFolderRepository folders;

	private final DocumentRepository documents;

	DocumentFolderService(AccessService access, DocumentLoader loader, DocumentFolderRepository folders,
			DocumentRepository documents) {
		this.access = access;
		this.loader = loader;
		this.folders = folders;
		this.documents = documents;
	}

	@Transactional(readOnly = true)
	public List<FolderResponse> list(UUID userId, UUID projectId) {
		access.project(userId, projectId);
		Map<UUID, Long> counts = new HashMap<>();
		for (Object[] row : documents.countsByFolder(projectId)) {
			if (row[0] != null) {
				counts.put((UUID) row[0], ((Number) row[1]).longValue());
			}
		}
		return folders.findAllByProjectIdOrderByNameAsc(projectId)
			.stream()
			.map((f) -> new FolderResponse(f.getId(), f.getParentId(), f.getName(), counts.getOrDefault(f.getId(), 0L)))
			.toList();
	}

	public FolderResponse create(UUID userId, UUID projectId, DocumentRequests.SaveFolder request) {
		access.project(userId, projectId).require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		List<DocumentFolder> all = folders.findAllByProjectIdOrderByNameAsc(projectId);
		if (request.parentId() != null) {
			DocumentFolder parent = all.stream().filter((f) -> f.getId().equals(request.parentId())).findFirst()
				.orElseThrow(DocumentLoader::folderNotFound);
			if (depth(parent, all) >= MAX_DEPTH) {
				throw ApiException.badRequest("FOLDER_TOO_DEEP", "Folders can be nested at most " + MAX_DEPTH + " levels");
			}
		}
		String name = request.name().strip();
		requireUnique(all, request.parentId(), name, null);
		DocumentFolder folder = folders.saveAndFlush(new DocumentFolder(projectId, request.parentId(), name, userId));
		return new FolderResponse(folder.getId(), folder.getParentId(), folder.getName(), 0);
	}

	public FolderResponse rename(UUID userId, UUID folderId, DocumentRequests.SaveFolder request) {
		LoadedFolder loaded = loader.loadFolder(userId, folderId);
		loaded.access().require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		DocumentFolder folder = loaded.folder();
		String name = request.name().strip();
		requireUnique(folders.findAllByProjectIdOrderByNameAsc(folder.getProjectId()), folder.getParentId(), name,
				folder.getId());
		folder.rename(name);
		return new FolderResponse(folder.getId(), folder.getParentId(), folder.getName(), 0);
	}

	public void delete(UUID userId, UUID folderId) {
		LoadedFolder loaded = loader.loadFolder(userId, folderId);
		ProjectAccess caller = loaded.access();
		caller.require(ProjectPermission.EDIT_DOCUMENTS).requireActive();
		if (folders.existsByParentId(folderId) || documents.existsByFolderId(folderId)) {
			throw ApiException.conflict("FOLDER_NOT_EMPTY", "Move or delete what is inside the folder first");
		}
		folders.delete(loaded.folder());
	}

	/** Number of levels from the root down to and including {@code folder}. */
	private static int depth(DocumentFolder folder, List<DocumentFolder> all) {
		Map<UUID, UUID> parentOf = new HashMap<>();
		all.forEach((f) -> parentOf.put(f.getId(), f.getParentId()));
		int depth = 1;
		for (UUID parent = folder.getParentId(); parent != null && depth <= MAX_DEPTH; parent = parentOf.get(parent)) {
			depth++;
		}
		return depth;
	}

	private static void requireUnique(List<DocumentFolder> all, UUID parentId, String name, UUID except) {
		boolean taken = all.stream()
			.filter((f) -> !f.getId().equals(except) && java.util.Objects.equals(f.getParentId(), parentId))
			.anyMatch((f) -> f.getName().equalsIgnoreCase(name));
		if (taken) {
			throw ApiException.conflict("FOLDER_NAME_TAKEN", "A folder with this name already exists here");
		}
	}

}
