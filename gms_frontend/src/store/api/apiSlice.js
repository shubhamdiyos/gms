import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

// Get API base URL from environment variable or use default
const getApiBaseUrl = () => {
  // Use environment variable if provided (recommended for production)
  const customUrl = import.meta.env.VITE_API_BASE_URL;
  
  if (customUrl) {
    return customUrl;
  }
  
  // Default backend URL - using HTTP for now
  // IMPORTANT: For production on Vercel, you MUST set VITE_API_BASE_URL with HTTPS
  // because Vercel serves over HTTPS and blocks HTTP requests (Mixed Content policy)
  const backendHost = 'ec2-65-0-109-47.ap-south-1.compute.amazonaws.com:8080';
  return `http://${backendHost}/api/v1`;
};

export const apiSlice = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({
    baseUrl: getApiBaseUrl(),
    prepareHeaders: (headers, { getState }) => {
      const token = getState().auth.token || localStorage.getItem('gms_token');
      if (token) {
        headers.set('authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
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

    // Parent role-specific endpoints
    getParentProfile: builder.query({
      query: () => '/parents/profile',
    }),
    
    getParentChildren: builder.query({
      query: () => '/parents/children',
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
        url: `/subjects/${id}`,
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
        url: '/academic-years/create',
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
      query: (params) => ({
        url: '/attendance',
        params,
      }),
      providesTags: ['Attendance'],
    }),
    
    markAttendance: builder.mutation({
      query: (attendanceData) => ({
        url: '/attendance/mark',
        method: 'POST',
        body: attendanceData,
      }),
      invalidatesTags: ['Attendance'],
    }),

    // Fee endpoints
    getFees: builder.query({
      query: () => '/fees',
      providesTags: ['Fee'],
    }),
    
    createFee: builder.mutation({
      query: (fee) => ({
        url: '/fees/create',
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
        url: '/announcements/create',
        method: 'POST',
        body: announcement,
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

    // Exam endpoints
    getExams: builder.query({
      query: () => '/exams',
      providesTags: ['Exam'],
    }),
    
    createExam: builder.mutation({
      query: (exam) => ({
        url: '/exams/create',
        method: 'POST',
        body: exam,
      }),
      invalidatesTags: ['Exam'],
    }),

    // Result endpoints
    getResults: builder.query({
      query: () => '/results',
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
  
  // Fees
  useGetFeesQuery,
  useCreateFeeMutation,
  useUpdateFeeMutation,
  useDeleteFeeMutation,
  
  // Notifications
  useGetNotificationsQuery,
  useMarkNotificationReadMutation,
  
  // Announcements
  useGetAnnouncementsQuery,
  useCreateAnnouncementMutation,
  
  // Sections
  useGetSectionsQuery,
  useCreateSectionMutation,
  
  // Exams
  useGetExamsQuery,
  useCreateExamMutation,
  
  // Results
  useGetResultsQuery,
} = apiSlice;
