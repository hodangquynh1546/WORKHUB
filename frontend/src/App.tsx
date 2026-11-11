import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthProvider'
import { RedirectIfAuthenticated, RequireAuth } from './components/Guards'
import { Layout } from './components/Layout'
import { IssuePage } from './pages/IssuePage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { InvitationPage } from './pages/InvitationPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { BacklogPage } from './pages/project/BacklogPage'
import { BoardPage } from './pages/project/BoardPage'
import { SprintPage } from './pages/project/SprintPage'
import { SprintsPage } from './pages/project/SprintsPage'
import { MembersPage } from './pages/project/MembersPage'
import { OverviewPage } from './pages/project/OverviewPage'
import { ProjectLayout } from './pages/project/ProjectLayout'
import { DocumentPage } from './pages/documents/DocumentPage'
import { DocumentsPage } from './pages/documents/DocumentsPage'
import { CalendarPage } from './pages/meetings/CalendarPage'
import { MeetingPage } from './pages/meetings/MeetingPage'
import { MeetingsPage } from './pages/meetings/MeetingsPage'
import { MyActivityPage, ProjectAuditPage, WorkspaceAuditPage } from './pages/AuditPage'
import { ChatPage } from './pages/chat/ChatPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { ProjectChatRedirect } from './pages/chat/ProjectChatRedirect'
import { RealtimeProvider } from './realtime/RealtimeProvider'
import { ReportsPage } from './pages/project/ReportsPage'
import { SettingsPage } from './pages/project/SettingsPage'
import { TeamPage } from './pages/TeamPage'
import { VerifyEmailPage } from './pages/VerifyEmailPage'
import { WorkspacePage } from './pages/WorkspacePage'
import { WorkspacesPage } from './pages/WorkspacesPage'
import { TeamsPage } from './pages/TeamsPage'
import { ProjectsPage } from './pages/ProjectsPage'

export default function App() {
  return (
    <AuthProvider>
      <RealtimeProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<RedirectIfAuthenticated />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>
          {/* Opened from emailed or shared links, signed in or not. */}
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/invitations/:token" element={<InvitationPage />} />
          <Route element={<RequireAuth />}>
            <Route element={<Layout />}>
              <Route path="/workspaces" element={<WorkspacesPage />} />
              <Route path="/workspaces/:workspaceId" element={<WorkspacePage />} />
              <Route path="/teams" element={<TeamsPage />} />
              <Route path="/projects" element={<ProjectsPage />} />
              <Route path="/chat" element={<ChatPage />} />
              <Route path="/chat/:conversationId" element={<ChatPage />} />
              <Route path="/notifications" element={<NotificationsPage />} />
              <Route path="/activity" element={<MyActivityPage />} />
              <Route path="/workspaces/:workspaceId/audit" element={<WorkspaceAuditPage />} />
              <Route path="/teams/:teamId" element={<TeamPage />} />
              <Route path="/issues/:issueId" element={<IssuePage />} />
              <Route path="/projects/:projectId" element={<ProjectLayout />}>
                <Route index element={<OverviewPage />} />
                <Route path="backlog" element={<BacklogPage />} />
                <Route path="sprints" element={<SprintsPage />} />
                <Route path="sprints/:sprintId" element={<SprintPage />} />
                <Route path="board" element={<BoardPage />} />
                <Route path="documents" element={<DocumentsPage />} />
                <Route path="documents/:documentId" element={<DocumentPage />} />
                <Route path="meetings" element={<MeetingsPage />} />
                <Route path="meetings/:meetingId" element={<MeetingPage />} />
                <Route path="calendar" element={<CalendarPage />} />
                <Route path="chat" element={<ProjectChatRedirect />} />
                <Route path="reports" element={<ReportsPage />} />
                <Route path="members" element={<MembersPage />} />
                <Route path="audit" element={<ProjectAuditPage />} />
                <Route path="settings" element={<SettingsPage />} />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/workspaces" replace />} />
        </Routes>
      </BrowserRouter>
      </RealtimeProvider>
    </AuthProvider>
  )
}
