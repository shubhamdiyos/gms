import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { mockBaseQuery } from '../../services/mockBaseQuery';

// Get API base URL from environment variable or use default
const getApiBaseUrl = () => {
  const customUrl = import.meta.env.VITE_API_BASE_URL;
  if (customUrl) {
    return customUrl;
  }
  if (import.meta.env.PROD) {
    return '/api/v1';
  }
  return 'http://localhost:8080/api/v1';
};

const MOCK_AUTH = import.meta.env.VITE_MOCK_AUTH === 'true';

const realBaseQuery = fetchBaseQuery({
  baseUrl: getApiBaseUrl(),
  prepareHeaders: (headers, { getState }) => {
    const token = getState().auth.token || localStorage.getItem('gms_token');
    if (token) {
      headers.set('authorization', `Bearer ${token}`);
    }
    return headers;
  },
});

export const apiSlice = createApi({
  reducerPath: 'api',
  // When VITE_MOCK_AUTH=true → use in-memory mock; otherwise → real HTTP
  baseQuery: MOCK_AUTH ? mockBaseQuery() : realBaseQuery,
  tagTypes: [
    'School',
    'Student', 
    'Employee',
    'User',
    'Classroom',
    'Subject',
    'Section',
    'AcademicYear',
    'Attendance',
    'Fee',
    'Exam',
    'Result',
    'Notification',
    'Announcement',
    'Parent',
    // The dashboard cards aggregate every entity below, so a row that moves a count must refetch them.
    'Dashboard'
  ],
  endpoints: (builder) => ({
    changePassword: builder.mutation({
      query: (passwordData) => ({
        url: '/auth/change-password',
        method: 'POST',
        body: passwordData,
      }),
    }),

    /**
     * This caller's school, counted in one round trip (DashboardController.getSchoolDashboard).
     *
     * `GET {base}/dashboard/school`, guarded `hasAnyAuthority('ADMIN', 'SUPERADMIN')`, answering
     * SchoolDashboardResponse: `schoolId`, `totalStudents`, `totalEmployees`, `totalClassrooms`,
     * `totalSections`, `totalExams` (all counts), `feesCollectedAmount` / `feesUncollectedAmount`
     * (BigDecimal, so JSON numbers the service already coerces from `null` to `0`) and `generatedAt`
     * (an ISO-8601 `Instant`).
     *
     * The tenant is the token's claim, never a parameter, so there is nothing to pass here. A caller
     * whose token carries no `schoolId` is answered 401 by design: the alternative is a page of zeros
     * that reads like a real answer. AdminDashboard renders this and must keep a failed read visible
     * rather swallow it into `0`.
     */
    getSchoolDashboard: builder.query({
      query: () => '/dashboard/school',
      providesTags: ['Dashboard'],
    }),

    /**
     * Every tenant on the platform, counted (DashboardController.getPlatformDashboard).
     *
     * `GET {base}/dashboard/platform`, guarded `hasAuthority('SUPERADMIN')` — a school ADMIN gets 403
     * before a query runs, so callers must skip this hook unless the user holds SUPERADMIN. Answers
     * PlatformDashboardResponse: `totalSchools`, `totalUsers`, `totalStudents`, `totalEmployees` and
     * `generatedAt`. No money fields: cross-tenant sums have no tenant to attribute them to.
     */
    getPlatformDashboard: builder.query({
      query: () => '/dashboard/platform',
      providesTags: ['Dashboard'],
    }),

    // School endpoints (SuperAdmin)
    getSchools: builder.query({
      query: () => '/schools',
      providesTags: ['School'],
    }),
    
    createSchool: builder.mutation({
      query: (school) => ({
        url: '/schools/create',
        method: 'POST',
        body: school,
      }),
      invalidatesTags: ['School', 'Dashboard'],
    }),
    
    updateSchool: builder.mutation({
      query: (school) => ({
        url: '/schools/update',
        method: 'PUT',
        body: school,
      }),
      invalidatesTags: ['School', 'Dashboard'],
    }),
    
    toggleSchool: builder.mutation({
      query: ({ id, isActive }) => ({
        url: `/schools/toggle?id=${id}&isActive=${isActive}`,
        method: 'PATCH',
      }),
      invalidatesTags: ['School', 'Dashboard'],
    }),
    
    getSchoolById: builder.query({
      query: (id) => `/schools/${id}`,
      providesTags: ['School'],
    }),

    // Student endpoints
    getStudents: builder.query({
      query: () => '/students',
      providesTags: ['Student'],
    }),

    /**
     * Server-side student search, filtering and pagination (StudentController.search).
     *
     * The route is `GET /students/search?query=&classId=&page=&size=&sort=` and it answers a Spring
     * Data `Page` of `StudentResponse` (`content[]` + `totalElements`/`totalPages`/`number`/`size`/`sort`),
     * which is a different shape from `GET /students` (a raw `List<Student>` entity dump whose derived
     * keys are snake_case: `full_name`, `class_id`). `getStudents` is left untouched because
     * AdminDashboard and ResultsPage still read it.
     *
     * Only parameters that are actually set go into the URL: the backend declares every one of
     * `query`/`classId`/`sort` optional and `page`/`size` defaulted, so sending `query=undefined` or an
     * empty `classId=` would bind a blank string instead of "no filter".
     */
    getStudentSearch: builder.query({
      query: (args = {}) => {
        const { query, classId, page, size, sort } = args ?? {};
        const params = new URLSearchParams();
        if (query != null && String(query).trim() !== '') {
          params.set('query', String(query).trim());
        }
        if (classId != null && classId !== '' && Number.isFinite(Number(classId))) {
          params.set('classId', String(Number(classId)));
        }
        // `page` is 0-based server-side, so 0 is a meaningful value and must still be sent.
        if (page != null && Number.isFinite(Number(page))) {
          params.set('page', String(Number(page)));
        }
        if (size != null && Number.isFinite(Number(size))) {
          params.set('size', String(Number(size)));
        }
        if (sort != null && String(sort).trim() !== '') {
          params.set('sort', String(sort).trim());
        }
        // URLSearchParams encodes a space as '+'. Tomcat decodes '+' in a query string back to a
        // space, but '%20' is unambiguous through every proxy in between; a literal '+' the user
        // typed is already emitted as '%2B', so this substitution can only touch encoded spaces.
        const search = params.toString().replace(/\+/g, '%20');
        return { url: `/students/search${search ? `?${search}` : ''}`, method: 'GET' };
      },
      providesTags: ['Student'],
    }),
    
    createStudent: builder.mutation({
      query: (student) => ({
        url: '/students/create',
        method: 'POST',
        body: student,
      }),
      invalidatesTags: ['Student', 'Dashboard'],
    }),
    
    updateStudent: builder.mutation({
      query: (student) => ({
        url: '/students/update',
        method: 'PUT',
        body: student,
      }),
      invalidatesTags: ['Student', 'Dashboard'],
    }),
    
    toggleStudent: builder.mutation({
      query: ({ id, isActive }) => ({
        url: `/students/toggle?id=${id}&isActive=${isActive}`,
        method: 'PATCH',
      }),
      invalidatesTags: ['Student', 'Dashboard'],
    }),

    // Student role-specific endpoints
    getStudentProfile: builder.query({
      query: () => '/students/profile',
      providesTags: ['Student'],
    }),
    
    getMyClasses: builder.query({
      query: () => '/students/my-classes',
    }),
    
    getMySubjects: builder.query({
      query: () => '/students/my-subjects',
    }),
    
    getMyAttendance: builder.query({
      query: () => '/students/my-attendance',
    }),
    
    getMyResults: builder.query({
      query: () => '/students/my-results',
    }),
    
    getMyFees: builder.query({
      query: () => '/students/my-fees',
    }),

    // Employee endpoints
    getEmployees: builder.query({
      query: () => '/employees',
      providesTags: ['Employee'],
    }),
    
    createEmployee: builder.mutation({
      query: (employee) => ({
        url: '/employees/create',
        method: 'POST',
        body: employee,
      }),
      invalidatesTags: ['Employee', 'Dashboard'],
    }),
    
    updateEmployee: builder.mutation({
      query: (employee) => ({
        url: '/employees/update',
        method: 'PUT',
        body: employee,
      }),
      invalidatesTags: ['Employee', 'Dashboard'],
    }),

    toggleEmployee: builder.mutation({
      query: ({ id, isActive }) => ({
        url: `/employees/toggle?id=${id}&isActive=${isActive}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Employee', 'Dashboard'],
    }),

    // Teacher role-specific endpoints
    getTeacherProfile: builder.query({
      query: () => '/teachers/profile',
    }),
    
    getTeacherClasses: builder.query({
      query: () => '/teachers/classes',
    }),
    
    getTeacherStudents: builder.query({
      query: () => '/teachers/students',
    }),

    // Parents
    getParentProfile: builder.query({
      query: () => '/parents/profile',
      providesTags: ['Parent'],
    }),

    getParentChildren: builder.query({
      query: () => '/parents/children',
      providesTags: ['Parent'],
    }),

    getChildAttendance: builder.query({
      query: (studentId) => `/parents/children/${studentId}/attendance`,
      providesTags: ['Attendance'],
    }),

    getChildResults: builder.query({
      query: (studentId) => `/parents/children/${studentId}/results`,
      providesTags: ['Result'],
    }),

    getChildFees: builder.query({
      query: (studentId) => `/parents/children/${studentId}/fees`,
      providesTags: ['Fee'],
    }),

    // Classroom endpoints
    getClassrooms: builder.query({
      query: () => '/classrooms',
      providesTags: ['Classroom'],
    }),
    
    createClassroom: builder.mutation({
      query: (classroom) => ({
        url: '/classrooms/create',
        method: 'POST',
        body: classroom,
      }),
      invalidatesTags: ['Classroom', 'Dashboard'],
    }),
    
    updateClassroom: builder.mutation({
      query: (classroom) => ({
        url: '/classrooms/update',
        method: 'PUT',
        body: classroom,
      }),
      invalidatesTags: ['Classroom', 'Dashboard'],
    }),
    
    deleteClassroom: builder.mutation({
      query: (id) => ({
        url: `/classrooms/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Classroom', 'Dashboard'],
    }),

    // Subject endpoints
    getSubjects: builder.query({
      query: () => '/subjects',
      providesTags: ['Subject'],
    }),
    
    createSubject: builder.mutation({
      query: (subject) => ({
        url: '/subjects/create',
        method: 'POST',
        body: subject,
      }),
      invalidatesTags: ['Subject'],
    }),
    
    updateSubject: builder.mutation({
      query: ({ id, ...subject }) => ({
        url: `/subjects/update/${id}`,
        method: 'PUT',
        body: subject,
      }),
      invalidatesTags: ['Subject'],
    }),
    
    deleteSubject: builder.mutation({
      query: (id) => ({
        url: `/subjects/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Subject'],
    }),

    // Academic Year endpoints
    getAcademicYears: builder.query({
      query: () => '/academic-years',
      providesTags: ['AcademicYear'],
    }),
    
    createAcademicYear: builder.mutation({
      query: (academicYear) => ({
        url: '/academic-years',
        method: 'POST',
        body: academicYear,
      }),
      invalidatesTags: ['AcademicYear'],
    }),
    
    updateAcademicYear: builder.mutation({
      query: ({ id, ...academicYear }) => ({
        url: `/academic-years/${id}`,
        method: 'PUT',
        body: academicYear,
      }),
      invalidatesTags: ['AcademicYear'],
    }),
    
    deleteAcademicYear: builder.mutation({
      query: (id) => ({
        url: `/academic-years/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['AcademicYear'],
    }),

    // Attendance endpoints
    getAttendance: builder.query({
      query: ({ classId, date }) => ({
        url: `/attendance/classroom/${classId}`,
        params: { date },
      }),
      providesTags: ['Attendance'],
    }),
    
    markAttendance: builder.mutation({
      query: (attendance) => ({
        url: '/attendance',
        method: 'POST',
        body: attendance,
      }),
      invalidatesTags: ['Attendance'],
    }),

    getStudentAttendanceRange: builder.query({
      query: ({ studentId, startDate, endDate }) => ({
        url: `/attendance/student/${studentId}/range`,
        params: { startDate, endDate },
      }),
      providesTags: ['Attendance'],
    }),

    // Fee endpoints
    getFees: builder.query({
      query: () => '/fees',
      providesTags: ['Fee'],
    }),
    
    createFee: builder.mutation({
      query: (fee) => ({
        url: '/fees',
        method: 'POST',
        body: fee,
      }),
      invalidatesTags: ['Fee'],
    }),
    
    updateFee: builder.mutation({
      query: ({ id, ...fee }) => ({
        url: `/fees/${id}`,
        method: 'PUT',
        body: fee,
      }),
      invalidatesTags: ['Fee'],
    }),
    
    deleteFee: builder.mutation({
      query: (id) => ({
        url: `/fees/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Fee'],
    }),

    getStudentFeesByStudent: builder.query({
      query: (studentId) => `/student-fees/student/${studentId}`,
      providesTags: ['Fee'],
    }),

    // Notification endpoints
    getNotifications: builder.query({
      query: () => '/notifications',
      providesTags: ['Notification'],
    }),
    
    markNotificationRead: builder.mutation({
      query: (id) => ({
        url: `/notifications/${id}/mark-read`,
        method: 'PUT',
      }),
      invalidatesTags: ['Notification'],
    }),

    // Announcement endpoints
    getAnnouncements: builder.query({
      query: () => '/announcements',
      providesTags: ['Announcement'],
    }),
    
    createAnnouncement: builder.mutation({
      query: (announcement) => ({
        url: '/announcements',
        method: 'POST',
        body: announcement,
      }),
      invalidatesTags: ['Announcement'],
    }),

    updateAnnouncement: builder.mutation({
      query: ({ id, ...announcement }) => ({
        url: `/announcements/${id}`,
        method: 'PUT',
        body: announcement,
      }),
      invalidatesTags: ['Announcement'],
    }),

    deleteAnnouncement: builder.mutation({
      query: (id) => ({
        url: `/announcements/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Announcement'],
    }),

    publishAnnouncement: builder.mutation({
      query: (id) => ({
        url: `/announcements/${id}/publish`,
        method: 'POST',
      }),
      invalidatesTags: ['Announcement'],
    }),

    unpublishAnnouncement: builder.mutation({
      query: (id) => ({
        url: `/announcements/${id}/unpublish`,
        method: 'POST',
      }),
      invalidatesTags: ['Announcement'],
    }),

    // Section endpoints
    getSections: builder.query({
      query: () => '/sections',
      providesTags: ['Section'],
    }),
    
    createSection: builder.mutation({
      query: (section) => ({
        url: '/sections',
        method: 'POST',
        body: section,
      }),
      invalidatesTags: ['Section', 'Dashboard'],
    }),

    // Timetable endpoints
    getTeacherTimetable: builder.query({
      query: () => '/timetables/teacher/me',
    }),

    getMyTimetable: builder.query({
      query: () => '/students/my-timetable',
    }),

    // Exam endpoints
    getExams: builder.query({
      query: () => '/exams',
      providesTags: ['Exam'],
    }),
    
    createExam: builder.mutation({
      query: (exam) => ({
        url: '/exams',
        method: 'POST',
        body: exam,
      }),
      invalidatesTags: ['Exam', 'Dashboard'],
    }),

    updateExam: builder.mutation({
      query: ({ id, ...exam }) => ({
        url: `/exams/${id}`,
        method: 'PUT',
        body: exam,
      }),
      invalidatesTags: ['Exam', 'Dashboard'],
    }),

    deleteExam: builder.mutation({
      query: (id) => ({
        url: `/exams/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Exam', 'Dashboard'],
    }),

    getExamsByAcademicYear: builder.query({
      query: (academicYear) => ({
        url: '/exams/academic-year',
        params: { academicYear },
      }),
      providesTags: ['Exam'],
    }),

    // Result endpoints
    getResults: builder.query({
      query: () => '/results', // reserved for potential future list endpoint
      providesTags: ['Result'],
    }),

    recordResult: builder.mutation({
      query: (result) => ({
        url: '/results',
        method: 'POST',
        body: result,
      }),
      invalidatesTags: ['Result'],
    }),

    updateResult: builder.mutation({
      query: ({ id, ...result }) => ({
        url: `/results/${id}`,
        method: 'PUT',
        body: result,
      }),
      invalidatesTags: ['Result'],
    }),

    deleteResult: builder.mutation({
      query: (id) => ({
        url: `/results/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Result'],
    }),

    getResultsForStudentExam: builder.query({
      query: (studentExamId) => `/results/student-exam/${studentExamId}`,
      providesTags: ['Result'],
    }),

    getResultsForStudent: builder.query({
      query: (studentId) => `/results/student/${studentId}`,
      providesTags: ['Result'],
    }),
  }),
});

// Export hooks for usage in functional components
export const {
  // Auth
  useChangePasswordMutation,

  // Dashboards (aggregate counts and sums; never derived from a list length in the browser)
  useGetSchoolDashboardQuery,
  useGetPlatformDashboardQuery,
  
  // Schools
  useGetSchoolsQuery,
  useCreateSchoolMutation,
  useUpdateSchoolMutation,
  useToggleSchoolMutation,
  useGetSchoolByIdQuery,
  
  // Students
  useGetStudentsQuery,
  useGetStudentSearchQuery,
  useCreateStudentMutation,
  useUpdateStudentMutation,
  useToggleStudentMutation,
  useGetStudentProfileQuery,
  useGetMyClassesQuery,
  useGetMySubjectsQuery,
  useGetMyAttendanceQuery,
  useGetMyResultsQuery,
  useGetMyFeesQuery,
  
  // Employees
  useGetEmployeesQuery,
  useCreateEmployeeMutation,
  useUpdateEmployeeMutation,
  
  // Teachers
  useGetTeacherProfileQuery,
  useGetTeacherClassesQuery,
  useGetTeacherStudentsQuery,
  
  // Parents
  useGetParentProfileQuery,
  useGetParentChildrenQuery,
  useGetChildAttendanceQuery,
  useGetChildResultsQuery,
  useGetChildFeesQuery,
  
  // Classrooms
  useGetClassroomsQuery,
  useCreateClassroomMutation,
  useUpdateClassroomMutation,
  useDeleteClassroomMutation,
  
  // Subjects
  useGetSubjectsQuery,
  useCreateSubjectMutation,
  useUpdateSubjectMutation,
  useDeleteSubjectMutation,
  
  // Academic Years
  useGetAcademicYearsQuery,
  useCreateAcademicYearMutation,
  useUpdateAcademicYearMutation,
  useDeleteAcademicYearMutation,
  
  // Attendance
  useGetAttendanceQuery,
  useMarkAttendanceMutation,
  useGetStudentAttendanceRangeQuery,
  
  // Fees
  useGetFeesQuery,
  useCreateFeeMutation,
  useUpdateFeeMutation,
  useDeleteFeeMutation,
  useGetStudentFeesByStudentQuery,
  
  // Notifications
  useGetNotificationsQuery,
  useMarkNotificationReadMutation,
  
  // Announcements
  useGetAnnouncementsQuery,
  useCreateAnnouncementMutation,
  useUpdateAnnouncementMutation,
  useDeleteAnnouncementMutation,
  usePublishAnnouncementMutation,
  useUnpublishAnnouncementMutation,
  
  // Sections
  useGetSectionsQuery,
  useCreateSectionMutation,
  
  // Timetable
  useGetTeacherTimetableQuery,
  useGetMyTimetableQuery,
  
  // Exams
  useGetExamsQuery,
  useCreateExamMutation,
  useUpdateExamMutation,
  useDeleteExamMutation,
  useGetExamsByAcademicYearQuery,
  
  // Results
  useGetResultsQuery,
  useRecordResultMutation,
  useUpdateResultMutation,
  useDeleteResultMutation,
  useGetResultsForStudentExamQuery,
  useGetResultsForStudentQuery,
} = apiSlice;
