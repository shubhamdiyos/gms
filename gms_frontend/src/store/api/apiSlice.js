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
    'Announcement'
  ],
  endpoints: (builder) => ({
    // Auth endpoints
    login: builder.mutation({
      query: (credentials) => ({
        url: '/auth/login',
        method: 'POST',
        body: credentials,
      }),
      transformResponse: (response) => ({
        token: response.accessToken,
        tokenType: response.tokenType,
        expiresIn: response.expiresIn,
        requirePasswordChange: response.requirePasswordChange,
      }),
    }),
    
    changePassword: builder.mutation({
      query: (passwordData) => ({
        url: '/auth/change-password',
        method: 'POST',
        body: passwordData,
      }),
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
      invalidatesTags: ['School'],
    }),
    
    updateSchool: builder.mutation({
      query: (school) => ({
        url: '/schools/update',
        method: 'PUT',
        body: school,
      }),
      invalidatesTags: ['School'],
    }),
    
    toggleSchool: builder.mutation({
      query: ({ id, isActive }) => ({
        url: `/schools/toggle?id=${id}&isActive=${isActive}`,
        method: 'PATCH',
      }),
      invalidatesTags: ['School'],
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
    
    createStudent: builder.mutation({
      query: (student) => ({
        url: '/students/create',
        method: 'POST',
        body: student,
      }),
      invalidatesTags: ['Student'],
    }),
    
    updateStudent: builder.mutation({
      query: (student) => ({
        url: '/students/update',
        method: 'PUT',
        body: student,
      }),
      invalidatesTags: ['Student'],
    }),
    
    toggleStudent: builder.mutation({
      query: ({ id, isActive }) => ({
        url: `/students/toggle?id=${id}&isActive=${isActive}`,
        method: 'PATCH',
      }),
      invalidatesTags: ['Student'],
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
      invalidatesTags: ['Employee'],
    }),
    
    updateEmployee: builder.mutation({
      query: (employee) => ({
        url: '/employees/update',
        method: 'PUT',
        body: employee,
      }),
      invalidatesTags: ['Employee'],
    }),

    toggleEmployee: builder.mutation({
      query: ({ id, isActive }) => ({
        url: `/employees/toggle?id=${id}&isActive=${isActive}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Employee'],
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
      invalidatesTags: ['Classroom'],
    }),
    
    updateClassroom: builder.mutation({
      query: ({ id, ...classroom }) => ({
        url: `/classrooms/${id}`,
        method: 'PUT',
        body: classroom,
      }),
      invalidatesTags: ['Classroom'],
    }),
    
    deleteClassroom: builder.mutation({
      query: (id) => ({
        url: `/classrooms/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Classroom'],
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
        url: `/notifications/${id}/read`,
        method: 'PATCH',
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
        url: '/sections/create',
        method: 'POST',
        body: section,
      }),
      invalidatesTags: ['Section'],
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
      invalidatesTags: ['Exam'],
    }),

    updateExam: builder.mutation({
      query: ({ id, ...exam }) => ({
        url: `/exams/${id}`,
        method: 'PUT',
        body: exam,
      }),
      invalidatesTags: ['Exam'],
    }),

    deleteExam: builder.mutation({
      query: (id) => ({
        url: `/exams/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Exam'],
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
  useLoginMutation,
  useChangePasswordMutation,
  
  // Schools
  useGetSchoolsQuery,
  useCreateSchoolMutation,
  useUpdateSchoolMutation,
  useToggleSchoolMutation,
  useGetSchoolByIdQuery,
  
  // Students
  useGetStudentsQuery,
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
